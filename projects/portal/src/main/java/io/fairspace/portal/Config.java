package io.fairspace.portal;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Config {
    public static final String WORKSPACE_CHART = "workspace";

    public final Auth auth = new Auth();
    public final Tiller tiller = new Tiller();
    public final Map<String, URL> charts = new HashMap<>();

    public static class Auth {
        public boolean enabled = false;

        public String jwksUrl = "https://keycloak.hyperspace.ci.fairway.app/auth/realms/ci/protocol/openid-connect/certs";

        public String jwtAlgorithm = "RS256";
    }

    public static class Tiller {
        public String namespace = "kube-system";
        public String service = "tiller-deploy";
        public int port = 44134;
    }

    @Override
    public String toString() {
        try {
            return new ObjectMapper(new YAMLFactory()).writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return super.toString();
        }
    }
}
