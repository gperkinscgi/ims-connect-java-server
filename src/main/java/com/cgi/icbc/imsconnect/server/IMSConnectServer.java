package com.cgi.icbc.imsconnect.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * High-performance IMS Connect server implementation using Netty NIO.
 * Accepts TCP connections from IMS clients and processes IRM messages.
 */
public class IMSConnectServer {

    private static final Logger logger = LoggerFactory.getLogger(IMSConnectServer.class);

    private final int port;
    private final IMSConnectServerHandler messageHandler;
    private final OTMAServerHandler otmaHandler;
    private final ServerConfiguration config;
    private final boolean otmaEnabled;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private SslContext sslContext;

    public IMSConnectServer(int port, IMSConnectServerHandler messageHandler) {
        this(port, messageHandler, null, ServerConfiguration.defaultConfig());
    }

    public IMSConnectServer(int port, IMSConnectServerHandler messageHandler, ServerConfiguration config) {
        this(port, messageHandler, null, config);
    }

    public IMSConnectServer(int port, IMSConnectServerHandler messageHandler, OTMAServerHandler otmaHandler, ServerConfiguration config) {
        this.port = port;
        this.messageHandler = messageHandler;
        this.otmaHandler = otmaHandler;
        this.config = config;
        this.otmaEnabled = otmaHandler != null;
    }

    /**
     * Starts the IMS Connect server.
     *
     * @return CompletableFuture that completes when server is fully started
     */
    public CompletableFuture<Void> start() {
        CompletableFuture<Void> startupFuture = new CompletableFuture<>();

        try {
            // Create event loop groups
            bossGroup = new NioEventLoopGroup(config.getBossThreads());
            workerGroup = new NioEventLoopGroup(config.getWorkerThreads());

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, config.getBacklogSize())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, config.isKeepAlive())
                    .childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay())
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // Add SSL handler if configured
                            if (sslContext != null) {
                                SSLEngine sslEngine = sslContext.newEngine(ch.alloc());
                                pipeline.addLast("ssl", new SslHandler(sslEngine));
                            }

                            // Add idle state handler for connection timeout
                            pipeline.addLast("idleState", new IdleStateHandler(
                                    config.getReadIdleTimeSeconds(),
                                    config.getWriteIdleTimeSeconds(),
                                    config.getAllIdleTimeSeconds()));

                            // Add IMS Connect protocol handlers
                            if (otmaEnabled) {
                                // OTMA-enabled pipeline with support for both legacy and OTMA messages
                                pipeline.addLast("otmaDecoder", new OTMAMessageDecoder());
                                pipeline.addLast("otmaEncoder", new OTMAMessageEncoder());
                                pipeline.addLast("protocolRouter", new ProtocolRoutingHandler(messageHandler, otmaHandler));
                            } else {
                                // Legacy IMS Connect pipeline
                                pipeline.addLast("imsDecoder", new IMSMessageDecoder());
                                pipeline.addLast("imsEncoder", new IMSMessageEncoder());
                                pipeline.addLast("imsHandler", new IMSChannelHandler(messageHandler, config));
                            }
                        }
                    });

            // Bind to port
            ChannelFuture bindFuture = bootstrap.bind(port);
            bindFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    serverChannel = future.channel();
                    logger.info("IMS Connect server started on port {}", port);
                    startupFuture.complete(null);
                } else {
                    logger.error("Failed to start IMS Connect server on port {}", port, future.cause());
                    startupFuture.completeExceptionally(future.cause());
                }
            });

        } catch (Exception e) {
            logger.error("Error starting IMS Connect server", e);
            startupFuture.completeExceptionally(e);
        }

        return startupFuture;
    }

    /**
     * Stops the IMS Connect server gracefully.
     *
     * @return CompletableFuture that completes when server is fully stopped
     */
    public CompletableFuture<Void> stop() {
        CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

        logger.info("Shutting down IMS Connect server...");

        CompletableFuture<Void> serverCloseFuture = CompletableFuture.completedFuture(null);

        // Close server channel if it exists
        if (serverChannel != null) {
            serverCloseFuture = nettyFutureToCompletableFuture(serverChannel.close());
        }

        serverCloseFuture.thenRun(() -> {
            // Shutdown event loop groups
            CompletableFuture<Void> bossShutdown = CompletableFuture.completedFuture(null);
            CompletableFuture<Void> workerShutdown = CompletableFuture.completedFuture(null);

            if (bossGroup != null) {
                bossShutdown = nettyFutureToCompletableFuture(
                        bossGroup.shutdownGracefully(0, config.getShutdownTimeoutSeconds(), TimeUnit.SECONDS));
            }

            if (workerGroup != null) {
                workerShutdown = nettyFutureToCompletableFuture(
                        workerGroup.shutdownGracefully(0, config.getShutdownTimeoutSeconds(), TimeUnit.SECONDS));
            }

            CompletableFuture.allOf(bossShutdown, workerShutdown)
                    .thenRun(() -> {
                        logger.info("IMS Connect server shutdown complete");
                        shutdownFuture.complete(null);
                    })
                    .exceptionally(throwable -> {
                        logger.error("Error during server shutdown", throwable);
                        shutdownFuture.completeExceptionally(throwable);
                        return null;
                    });
        }).exceptionally(throwable -> {
            logger.error("Error closing server channel", throwable);
            shutdownFuture.completeExceptionally(throwable);
            return null;
        });

        return shutdownFuture;
    }

    /**
     * Configures SSL/TLS for secure connections.
     *
     * @param sslContext the SSL context to use
     */
    public void configureSsl(SslContext sslContext) {
        this.sslContext = sslContext;
        logger.info("SSL/TLS configured for secure connections");
    }

    /**
     * Configures SSL/TLS using SSL configuration.
     *
     * @param sslConfig the SSL configuration
     * @throws Exception if SSL context creation fails
     */
    public void configureSsl(SSLConfiguration sslConfig) throws Exception {
        this.sslContext = sslConfig.buildSslContext();
        logger.info("SSL/TLS configured from SSL configuration");
    }

    /**
     * Gets the current server configuration.
     *
     * @return server configuration
     */
    public ServerConfiguration getConfig() {
        return config;
    }

    /**
     * Checks if the server is currently running.
     *
     * @return true if server is running, false otherwise
     */
    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    /**
     * Gets the port the server is bound to.
     *
     * @return server port
     */
    public int getPort() {
        return port;
    }

    private CompletableFuture<Void> nettyFutureToCompletableFuture(io.netty.util.concurrent.Future<Void> nettyFuture) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        nettyFuture.addListener(future -> {
            if (future.isSuccess()) {
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(future.cause());
            }
        });
        return completableFuture;
    }
}