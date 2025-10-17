package com.cgi.icbc.imsconnect.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Represents a pooled connection to a backend IMS system.
 */
public class PooledConnection {

    private static final Logger logger = LoggerFactory.getLogger(PooledConnection.class);

    private final BackendConfig backend;
    private final Channel channel;
    private final Instant creationTime;
    private volatile Instant lastUsedTime;
    private volatile boolean valid = true;

    public PooledConnection(BackendConfig backend) throws Exception {
        this.backend = backend;
        this.creationTime = Instant.now();
        this.lastUsedTime = creationTime;
        this.channel = createChannel();
    }

    private Channel createChannel() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) backend.getHealthCheckTimeoutMs())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // Add minimal pipeline for health checking
                            // In production, you'd add appropriate handlers
                        }
                    });

            ChannelFuture future = bootstrap.connect(backend.getHost(), backend.getPort())
                    .sync();

            if (future.isSuccess()) {
                logger.debug("Connected to backend: {} ({}:{})",
                           backend.getName(), backend.getHost(), backend.getPort());
                return future.channel();
            } else {
                throw new Exception("Failed to connect to backend: " + backend.getName());
            }

        } catch (Exception e) {
            group.shutdownGracefully();
            throw e;
        }
    }

    /**
     * Checks if the connection is still valid.
     *
     * @return true if connection is valid
     */
    public boolean isValid() {
        if (!valid || channel == null) {
            return false;
        }

        // Check if channel is active
        if (!channel.isActive()) {
            valid = false;
            return false;
        }

        // Check connection age (optional health check)
        long ageMs = java.time.Duration.between(creationTime, Instant.now()).toMillis();
        if (ageMs > 3600000) { // 1 hour max age
            valid = false;
            return false;
        }

        return true;
    }

    /**
     * Performs a health check on the connection.
     *
     * @return true if health check passes
     */
    public boolean healthCheck() {
        if (!isValid()) {
            return false;
        }

        try {
            // In a real implementation, you might send a ping message
            // For now, just check if channel is writable
            return channel.isWritable();

        } catch (Exception e) {
            logger.debug("Health check failed for backend: {}", backend.getName(), e);
            valid = false;
            return false;
        }
    }

    /**
     * Updates the last used timestamp.
     */
    public void updateLastUsed() {
        this.lastUsedTime = Instant.now();
    }

    /**
     * Closes the connection.
     */
    public void close() {
        valid = false;

        if (channel != null) {
            try {
                channel.close().sync();
                logger.debug("Closed connection to backend: {}", backend.getName());
            } catch (Exception e) {
                logger.debug("Error closing connection to backend: {}", backend.getName(), e);
            }
        }
    }

    /**
     * Gets the underlying Netty channel.
     *
     * @return the channel
     */
    public Channel getChannel() {
        updateLastUsed();
        return channel;
    }

    public String getBackendName() { return backend.getName(); }
    public BackendConfig getBackend() { return backend; }
    public Instant getCreationTime() { return creationTime; }
    public Instant getLastUsedTime() { return lastUsedTime; }

    @Override
    public String toString() {
        return String.format("PooledConnection{backend='%s', valid=%b, created=%s, lastUsed=%s}",
                           backend.getName(), valid, creationTime, lastUsedTime);
    }
}