package com.musicapp.config;

import com.musicapp.services.ImageCaptchaService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final Pattern CAPTCHA_TOKEN_PATTERN = Pattern.compile("name=\"captchaToken\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageGeneratesCaptcha() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"captchaCode\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("captcha-image")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(ImageCaptchaService.SESSION_ATTRIBUTE)).isInstanceOf(String.class);
    }

    @Test
    void invalidLoginCaptchaRedirectsBeforeAuthentication() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = loginPage.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(csrfCookie).isNotNull();
        assertThat(session).isNotNull();

        mockMvc.perform(post("/login")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "someone")
                        .param("password", "bad-password")
                        .param("captchaCode", "0000"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?captchaError"));
    }

    @Test
    void validLoginCaptchaReachesPasswordAuthentication() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = loginPage.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession session = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(csrfCookie).isNotNull();
        assertThat(session).isNotNull();

        String captchaToken = extractCaptchaToken(loginPage);
        String captchaCode = captchaCodeForToken(session, captchaToken);

        mockMvc.perform(post("/login")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "someone")
                        .param("password", "bad-password")
                        .param("captchaCode", captchaCode)
                        .param("captchaToken", captchaToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void captchaTokenPreventsLaterCaptchaFromOverwritingCurrentForm() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = firstPage.getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession session = (MockHttpSession) firstPage.getRequest().getSession(false);
        assertThat(csrfCookie).isNotNull();
        assertThat(session).isNotNull();

        String firstToken = extractCaptchaToken(firstPage);
        String firstCode = captchaCodeForToken(session, firstToken);

        mockMvc.perform(get("/login").session(session).cookie(csrfCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/login")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "someone")
                        .param("password", "bad-password")
                        .param("captchaCode", firstCode)
                        .param("captchaToken", firstToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void anonymousCanUseAiRecommendationEndpoint() throws Exception {
        MvcResult homePage = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie csrfCookie = homePage.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/ai/recommend")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("query", "nhac chill"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.tracks").isArray());
    }

    @Test
    void blankSearchRedirectsHome() throws Exception {
        mockMvc.perform(get("/search").param("query", "   "))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void anonymousCanOpenSearchPageWithQueryParam() throws Exception {
        mockMvc.perform(get("/search").param("query", "nhac chill"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kết quả hàng đầu")));
    }

    @Test
    void blankSearchSuggestionsReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private String extractCaptchaToken(MvcResult page) throws Exception {
        Matcher matcher = CAPTCHA_TOKEN_PATTERN.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @SuppressWarnings("unchecked")
    private String captchaCodeForToken(MockHttpSession session, String token) {
        Map<String, String> codes = (Map<String, String>) session.getAttribute(ImageCaptchaService.SESSION_CODES_ATTRIBUTE);
        assertThat(codes).containsKey(token);
        return codes.get(token);
    }
}
