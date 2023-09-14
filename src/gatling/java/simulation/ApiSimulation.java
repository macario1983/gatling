package simulation;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import net.datafaker.Faker;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import static java.time.Duration.of;
import static java.time.temporal.ChronoUnit.*;

public class ApiSimulation extends Simulation {

    private final Faker faker = new Faker(Locale.forLanguageTag("pt_BR"));

    private final Iterator<Map<String, Object>> feeder = Stream.generate(() -> {

        Map<String, Object> map = new HashMap<>();

        map.put("id", UUID.randomUUID());
        map.put("apelido", faker.name().firstName());
        map.put("nome", faker.name().fullName());
        map.put("nascimento", faker.date().birthday().toLocalDateTime());
        map.put("stack", generateProgrammingLangagueList());

        return map;

    }).iterator();

    private String generateProgrammingLangagueList() {
        List<String> languages = faker.collection(() -> faker.programmingLanguage().name()).len(1, 32).generate();
        return languages.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
    }

    /**
     * Criação do protocolo HTTP
     */
    private final HttpProtocolBuilder httpProtocolBuilder = http
            .baseUrl("http://localhost:8080")
            .userAgentHeader("Mozilla/5.0");

    /**
     * Criação do cenário de inserção de dados
     */
    private final ScenarioBuilder insertAndFindPerson = createInsertAndFindPersonScenario();

    private ScenarioBuilder createInsertAndFindPersonScenario() {
        return scenario("Inserção em massa de dados")
                .feed(feeder)
                .exec(http("criando")
                        .post("/pessoas")
                        .requestTimeout(of(2_000, MILLIS))
                        .header("Content-Type", "application/json")
                        .body(StringBody(
                                """
                                    {
                                      "id": "#{id}",
                                      "apelido": "#{apelido}",
                                      "nome": "#{nome}",
                                      "nascimento": "#{nascimento}",
                                      "stack": [#{stack}]
                                    }
                                """
                        ))
                        .asJson()
                        .check(status().in(201, 400, 422))
                        .check(status().saveAs("httpStatus"))
                        .checkIf(session -> Objects.equals(session.get("httpStatus"), "201"))
                        .then(
                                header("Location").saveAs("location")
                        )
                )
                .pause(of(1, MILLIS), of(30, MILLIS))
                .doIf(session -> session.contains("location"))
                .then(exec(http("consultando").get("#{location}")));
    }


    {
        setUp(insertAndFindPerson.injectOpen(
                    constantUsersPerSec(2).during(of(10, SECONDS)),
                    constantUsersPerSec(5).during(of(15, SECONDS)).randomized(),
                    rampUsersPerSec(6).to(600).during(of(3, MINUTES))))
                .protocols(httpProtocolBuilder);
    }

}
