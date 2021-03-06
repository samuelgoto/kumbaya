package com.kumbaya.router.handlers;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.kumbaya.common.Flags.Flag;
import com.kumbaya.common.testing.Supplier;
import com.kumbaya.dht.Dht;
import com.kumbaya.dht.Keys;
import com.kumbaya.router.Kumbaya;
import com.kumbaya.router.Packets.Data;
import com.kumbaya.router.Packets.Interest;
import com.kumbaya.router.TcpServer.Handler;
import com.kumbaya.router.TcpServer.Interface;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.limewire.mojito.db.DHTValueEntity;

class InterestHandler implements Handler<Interest> {
  private static final Log logger = LogFactory.getLog(InterestHandler.class);
  private static final ConcurrentMap<String, Supplier<ListenableFuture<Optional<Data>>>> pendingInterestTable =
      new ConcurrentHashMap<String, Supplier<ListenableFuture<Optional<Data>>>>();

  private @Inject @Flag("forwarding") String forwarding = "localhost:8082";
  private final Kumbaya client;
  private final Dht dht;

  @Inject
  InterestHandler(Kumbaya client, Dht dht) {
    this.client = client;
    this.dht = dht;
  }

  public Optional<InetSocketAddress> route(String url) {
    try {
      String domain = new URL(url).getAuthority();
      List<DHTValueEntity> entities = dht.get(Keys.as(Keys.of(domain)), 200);

      if (entities.size() == 0) {
        return Optional.absent();
      }

      // Just gets the first contact that returned a hit.
      // TODO(goto): figure out what to do when there are multiple hits.
      return Optional.of((InetSocketAddress) entities.get(0).getCreator().getContactAddress());
    } catch (MalformedURLException | InterruptedException | ExecutionException
        | TimeoutException e) {
      return Optional.absent();
    }
  }

  @Override
  public void handle(Interest request, Interface response) throws IOException {
    String key = request.getName().getName();

    logger.info("Handling a request: " + key);

    Supplier<ListenableFuture<Optional<Data>>> value =
        new Supplier<ListenableFuture<Optional<Data>>>() {
          @Override
          public synchronized ListenableFuture<Optional<Data>> build() {
            try {
              logger.info("Forwarding: " + key);
              Optional<InetSocketAddress> route = route(key);
              if (!route.isPresent()) {
                return Futures.immediateCancelledFuture();
              }
              Optional<Data> value = client.send(route.get(), request);
              return Futures.immediateFuture(value);
            } catch (IOException e) {
              return Futures.immediateCancelledFuture();
            }
          }
        };

    Supplier<ListenableFuture<Optional<Data>>> future =
        pendingInterestTable.putIfAbsent(key, value);

    if (future == null) {
      future = value;
    }

    // Forwards the interest to the next hop.
    Futures.addCallback(future.get(), new FutureCallback<Optional<Data>>() {
      @Override
      public void onFailure(Throwable throwable) {
        try {
          response.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void onSuccess(Optional<Data> result) {
        try {
          if (result.isPresent()) {
            logger.info("Got a Data packet, responding.");
            response.push(result.get());
            pendingInterestTable.remove(key);
          }
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          try {
            response.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    });
  }
}
