package livestreaming;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class LiveStreamingSimulation extends Simulation {
    HttpProtocolBuilder httpProtocol = http.baseUrl("http://reference.dashif.org")
                    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .acceptLanguageHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .acceptEncodingHeader("gzip, deflate")
                    .userAgentHeader(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0"
                    );


    ScenarioBuilder usersGoToTheStreaming = scenario("Users")
            .exec(
                    http("go on reference.dashif.org/dash.js/")
                            .get("http://reference.dashif.org/dash.js/")
            )
            .group("init connection").on(
                    exec(http("Navigate to the webpage")
                            .get("/dash.js/latest/samples/live-streaming/live-delay-comparison-custom-manifest.html"))
                            .exec(http("Get the streaming manifest")
                                    .get("https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd")
                                    .check(bodyString().saveAs("streaming_manifest_content"))
                            )
                            .exec(session -> {
                                System.out.println("streaming_manifest_ul : " + session.get("streaming_manifest_content"));
                                String xml = session.get("streaming_manifest_content");
                                String baseURL = StringUtils.substringBetween(xml, "<BaseURL>", "</BaseURL>");
                                String videoRepresentationID = "V300";
                                String audioRepresentationID = "A48";
                                return session.setAll(
                                        Map.ofEntries(
                                                Map.entry("videoRepresentationID", videoRepresentationID),
                                                Map.entry("audioRepresentationID", audioRepresentationID),
                                                Map.entry("baseURL", baseURL)
                                        )
                                );
                            }))
            .exec(exitHereIf(session -> StringUtils.isEmpty(session.get("baseURL"))))
            .exec(http("Get init video")
                    .get("#{baseURL}#{videoRepresentationID}/init.mp4"))
            .exec(http("Get init audio")
                    .get("#{baseURL}#{audioRepresentationID}/init.mp4"))
            .group("streaming").on(
                    during(60).on(
                            exec(session -> {
                                return computeSectionSegment(session);
                            })
                                    .exec(http("Get some audio")
                                            .get("#{baseURL}#{audioRepresentationID}/#{sectionSegment}.m4s"))
                                    .pause(Duration.ofMillis(200), Duration.ofMillis(500))
                                    .exec(session -> {
                                        return computeSectionSegment(session);
                                    })
                                    .exec(http("Get some video")
                                            .get("#{baseURL}#{videoRepresentationID}/#{sectionSegment}.m4s"))
                    ));

    private static Session computeSectionSegment(Session session) {
        long currentTimestamp = Instant.now().getEpochSecond();
        long sectionSegment = (long) Math.floor(currentTimestamp / 2) - 100; //FIX THE CLOCK

        return session.set("sectionSegment", String.valueOf(sectionSegment));
    }


    {
        setUp(
                usersGoToTheStreaming.injectOpen(constantUsersPerSec(10).during(120))).assertions(
                details("streaming").responseTime().max().lt(500)
        ).protocols(httpProtocol);
    }


//    {
//        setUp(
//                usersGoToTheStreaming.injectOpen(atOnceUsers(1))
//        ).protocols(httpProtocol);
//    }

}
