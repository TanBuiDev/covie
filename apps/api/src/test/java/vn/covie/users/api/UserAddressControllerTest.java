package vn.covie.users.api;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vn.covie.shared.web.GlobalExceptionHandler;
import vn.covie.users.application.UserAddressService;

class UserAddressControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserAddressController(mock(UserAddressService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void rejectsLatitudeAboveNinetyWithUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/api/v1/me/addresses").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":"Home","addressText":"1 Main Street","latitude":91,"longitude":106.8374,"pinSource":"GPS","defaultAddress":false}
                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsLongitudeAboveOneEightyWithUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/api/v1/me/addresses").contentType(MediaType.APPLICATION_JSON).content("""
                {"label":"Home","addressText":"1 Main Street","latitude":10.8412,"longitude":181,"pinSource":"GPS","defaultAddress":false}
                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
