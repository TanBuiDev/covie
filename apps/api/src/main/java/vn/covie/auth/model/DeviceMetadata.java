package vn.covie.auth.model;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
public record DeviceMetadata(String deviceName, String userAgent) {
  public static DeviceMetadata from(HttpServletRequest request) {
    String ua=Optional.ofNullable(request.getHeader("User-Agent")).orElse("Unknown"); if(ua.length()>500) ua=ua.substring(0,500);
    String device=Optional.ofNullable(request.getHeader("Sec-CH-UA-Platform")).orElse("Browser").replace("\"",""); if(device.length()>120) device=device.substring(0,120);
    return new DeviceMetadata(device,ua);
  }
}
