package com.line4.idea2ip.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTests {
    private final org.springframework.web.cors.CorsConfigurationSource source = new WebConfig()
            .corsConfigurationSource(" http://localhost:5173 , https://idea2ip.netlify.app ");

    private MockHttpServletResponse preflight(String origin) throws Exception {
        var request = new MockHttpServletRequest("OPTIONS", "/api/projects/1/report/versions");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "authorization, content-type");
        var response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }

    @Test void frontendOriginsArePermitted() throws Exception {
        for (String origin : new String[] {"http://localhost:5173", "https://idea2ip.netlify.app"}) {
            var response = preflight(origin);
            assertEquals(200, response.getStatus());
            assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
            assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        }
    }

    @Test void otherOriginsAreRejected() throws Exception {
        var response = preflight("https://evil.example");
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test void pdfFileNameIsReadableByTheBrowser() {
        var request = new MockHttpServletRequest("GET", "/api/projects/1/report/versions/1/pdf");
        assertTrue(source.getCorsConfiguration(request).getExposedHeaders().contains("Content-Disposition"));
    }
}
