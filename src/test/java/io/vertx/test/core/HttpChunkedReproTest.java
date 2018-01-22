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

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.net.JksOptions;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpChunkedReproTest extends HttpTestBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  class PayloadGenerator {
    private AtomicInteger length;
    private Handler<String> payloadHandler;
    private Handler<Void> endHandler;
    private Random random = new Random();

    PayloadGenerator(int length, Handler<String> payloadHandler, Handler<Void> endHandler) {
      this.length = new AtomicInteger(length);
      this.payloadHandler = payloadHandler;
      this.endHandler = endHandler;
    }

    public void start() {
      vertx.setPeriodic(1, id -> {
        int chunksLeft = length.decrementAndGet();
        if (chunksLeft <= 0) {
          vertx.setTimer(random.nextInt(20) + 1, id2 -> {
            System.out.println("endHandler called on " + Thread.currentThread().getName());
            endHandler.handle(null);
          });
          vertx.cancelTimer(id);
        } else {
          vertx.setTimer(2, id2 -> {
            try {
              Thread.sleep(random.nextInt(10) + 1);
            } catch (InterruptedException e) {
              System.out.println(e.getMessage());
            }
            payloadHandler.handle(makePayload());
          });
        }
      });
    }

    private String makePayload() {
      return TestUtils.randomAlphaString(random.nextInt(10000));
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
      HttpServerResponse response = req.response().setChunked(true).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        .setStatusCode(HttpURLConnection.HTTP_OK);
      System.out.println("requestHandler called on " + Thread.currentThread().getName());
      new PayloadGenerator(
        20000,
        response::write,
        v -> response
          .end()).start();
    });

    startServer();
    client.get(new RequestOptions().setSsl(true).setPort(DEFAULT_HTTPS_PORT).setHost(DEFAULT_HTTPS_HOST), resp -> {
    }).putHeader("Connection", "close")
      .connectionHandler(connection -> {
        assertTrue( "Connection is secure", connection.isSsl());
        connection.exceptionHandler(throwable -> {
          System.out.println(throwable);
          this.fail(throwable);
        });
      })
      .exceptionHandler(throwable -> {
        System.out.println(throwable);
        this.fail(throwable);
      })
      .end();

    await();
  }
}
