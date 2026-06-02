package hongik.finEdu.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "https://donbugi.site",
            "http://donbugi.site",
            "https://donbugi.xyz",
            "https://www.donbugi.xyz",
            "http://donbugi.xyz",
            "https://donbugife.vercel.app"
    );
}
