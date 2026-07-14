package com.browicy.engine.net;

import java.io.IOException;
import java.net.URI;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class SubresourceAccessPolicyTest {

    @Test
    public void blocksCrossOriginLoopbackButAllowsPageOwnOrigin() throws IOException {
        URI target = URI.create("http://127.0.0.1:8080/font.ttf");

        assertThrows(IOException.class, () -> SubresourceAccessPolicy.validate(
                target, URI.create("https://example.test/")));
        SubresourceAccessPolicy.validate(target, URI.create("http://127.0.0.1:8080/page"));
    }
}
