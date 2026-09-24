package court.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@Controller
public class PageController {

    @GetMapping("/")
    public ResponseEntity<String> index() throws Exception {
        byte[] body = new ClassPathResource("static/index.html").getInputStream().readAllBytes();
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(new String(body, StandardCharsets.UTF_8));
    }
}
