package vn.covie.auth.api;
import jakarta.servlet.http.HttpServletRequest; import jakarta.servlet.http.HttpServletResponse; import java.io.IOException; import org.springframework.web.bind.annotation.GetMapping; import org.springframework.web.bind.annotation.RequestMapping; import org.springframework.web.bind.annotation.RequestParam; import org.springframework.web.bind.annotation.RestController; import vn.covie.auth.oauth.GoogleAuthIntent;
@RestController @RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
 public static final String SESSION_INTENT="covie.google.auth.intent";
 @GetMapping("/start") public void start(@RequestParam(defaultValue="LOGIN") GoogleAuthIntent intent,HttpServletRequest request,HttpServletResponse response) throws IOException { request.getSession(true).setAttribute(SESSION_INTENT,intent.name()); response.sendRedirect("/api/v1/auth/oauth2/authorization/google"); }
}
