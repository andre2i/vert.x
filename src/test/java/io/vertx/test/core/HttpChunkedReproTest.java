/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientRequestImpl;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.*;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.Pump;
import org.junit.Test;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class HttpChunkedReproTest extends HttpTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  class PayloadGenerator {
    private AtomicInteger length;
    private int width;
    private Handler<String> payloadHandler;
    private Handler<Void> endHandler;
    private Random random = new Random();

    PayloadGenerator(int length, int width, Handler<String> payloadHandler, Handler<Void> endHandler) {
      this.length = new AtomicInteger(length);
      this.width = width;
      this.payloadHandler = payloadHandler;
      this.endHandler = endHandler;
    }

    public void start() {
      vertx.setPeriodic(1, id -> {
        int chunksLeft = length.decrementAndGet();
        if (chunksLeft <= 0) {
          vertx.setTimer(random.nextInt(9) + 1, id2 -> {
            endHandler.handle(null);
          });
          vertx.cancelTimer(id);
        } else {
          payloadHandler.handle(makePayload());
        }
      });
    }

    private String makePayload() {
      return TestUtils.randomAlphaString(width);
    }
  }

  @Test
  public void testChuncked() throws Exception {
    client = vertx.createHttpClient(new HttpClientOptions().setTrustOptions(new JksOptions().setPath("tls/client-truststore.jks").setPassword("wibble")));
    server.close();
    server = vertx.createHttpServer(new HttpServerOptions()
      .setPort(DEFAULT_HTTPS_PORT)
      .setHost(DEFAULT_HTTPS_HOST)
      .setKeyCertOptions(new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble"))
      .setSsl(true)
    );

    server.requestHandler(req -> {
      HttpServerResponse response = req.response().setChunked(true);
      new PayloadGenerator(
        10000,
        10000,
        response::write,
        v -> response
          .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
          .setStatusCode(HttpURLConnection.HTTP_OK)
          .end()).start();
    });

    startServer();
      client.get(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost(DEFAULT_HTTPS_HOST), resp -> {
        resp.bodyHandler(buff -> {
        //  assertEquals(String.join("", chunks), buff.toString());
          complete();
        });
      }).putHeader("Connection", "close")
        .exceptionHandler(this::fail)
        .end();

      await();
  }
}
