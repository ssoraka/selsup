import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final TimeUnit timeUnit;
    private Object lock = new Object();

    private volatile TimeNode list;
    private volatile TimeNode listEnd;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        for (int i = 0; i < requestLimit; i++) {
            list = new TimeNode(System.nanoTime() - timeUnit.toNanos(2), list);
            if (i == 0) {
                listEnd = list;
            }
        }
        listEnd.next = list;
    }

    public HttpResponse<String> sendRequest(JSONObject json, String token) throws IOException, InterruptedException, URISyntaxException {

        while (true) {
            long sleep;
            long currentTime = System.nanoTime();
            long beforeRequest = currentTime - timeUnit.toNanos(1);

            synchronized (lock) {
                if (list.time < beforeRequest) {
                    TimeNode tmp = list;
                    list = list.next;
                    listEnd.next = tmp;
                    listEnd = tmp;
                    tmp.time = currentTime;
                    break;
                }
                sleep = currentTime - list.time;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(sleep);
            } catch (InterruptedException ignore) {}
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.ofString(json.toJSONString()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException, ParseException {
        JSONObject body = (JSONObject) new JSONParser().parse(args[0]);
        String token = args[1];

        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1);
        HttpResponse<String> response = api.sendRequest(body, token);
        System.out.println(response.statusCode());
        System.out.println(response.body());
    }
}

class TimeNode {
    long time;
    TimeNode next;

    public TimeNode(long time, TimeNode next) {
        this.time = time;
        this.next = next;
    }

}
