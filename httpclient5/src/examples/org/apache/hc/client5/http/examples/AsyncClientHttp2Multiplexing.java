/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.examples;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.async.AsyncClientEndpoint;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;

/**
 * This example demonstrates concurrent (multiplexed) execution of multiple
 * HTTP/2 message exchanges.
 */
public class AsyncClientHttp2Multiplexing {

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setConnectTimeout(5000)
                .setSoTimeout(5000)
                .build();

        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setIOReactorConfig(ioReactorConfig)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setH2Config(h2Config)
                .build();

        client.start();

        final HttpHost target = new HttpHost("http2bin.org");
        final Future<AsyncClientEndpoint> leaseFuture = client.lease(target, null);
        final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
        try {
            final String[] requestUris = new String[] {"/", "/ip", "/user-agent", "/headers"};

            final CountDownLatch latch = new CountDownLatch(requestUris.length);
            for (final String requestUri: requestUris) {
                final SimpleHttpRequest request = new SimpleHttpRequest("GET", target, requestUri, null, null);
                endpoint.execute(
                        new SimpleRequestProducer(request),
                        new SimpleResponseConsumer(),
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse response) {
                                latch.countDown();
                                System.out.println(requestUri + "->" + response.getCode());
                                System.out.println(response.getBody());
                            }

                            @Override
                            public void failed(final Exception ex) {
                                latch.countDown();
                                System.out.println(requestUri + "->" + ex);
                            }

                            @Override
                            public void cancelled() {
                                latch.countDown();
                                System.out.println(requestUri + " cancelled");
                            }

                        });
            }
            latch.await();
        } finally {
            endpoint.releaseAndReuse();
        }

        System.out.println("Shutting down");
        client.shutdown(5, TimeUnit.SECONDS);
    }

}
