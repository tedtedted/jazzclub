package com.tedredington.jazzclub.network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;

/**
 * pianobar's {@code bind_to}, which is curl's {@code CURLOPT_INTERFACE}: {@code if!tun0} for a network
 * interface, {@code host!192.0.2.1} or a bare address or host name for a local address. Used to send
 * Pandora traffic through a VPN without routing everything through it.
 */
public final class LocalAddress {

    private static final String INTERFACE_PREFIX = "if!";
    private static final String HOST_PREFIX = "host!";

    private LocalAddress() {
    }

    /** @throws IllegalArgumentException with a message fit for the user */
    public static InetAddress resolve(String bindTo) {
        String value = bindTo.strip();
        try {
            if (value.startsWith(INTERFACE_PREFIX)) {
                return firstAddressOf(value.substring(INTERFACE_PREFIX.length()));
            }
            String host = value.startsWith(HOST_PREFIX) ? value.substring(HOST_PREFIX.length()) : value;
            NetworkInterface byName = NetworkInterface.getByName(host);
            // curl tries the value as an interface name first when there is no prefix
            return byName != null && !value.startsWith(HOST_PREFIX)
                    ? firstAddressOf(host)
                    : InetAddress.getByName(host);
        } catch (UnknownHostException | SocketException e) {
            throw new IllegalArgumentException("bind_to is invalid: cannot resolve '" + value + "'", e);
        }
    }

    private static InetAddress firstAddressOf(String interfaceName) throws SocketException {
        NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
        if (networkInterface == null) {
            throw new IllegalArgumentException("bind_to is invalid: no network interface '" + interfaceName + "'");
        }
        return Collections.list(networkInterface.getInetAddresses()).stream()
                // an IPv4 address if there is one, as Pandora is reached over IPv4
                .min((a, b) -> Integer.compare(a.getAddress().length, b.getAddress().length))
                .orElseThrow(() -> new IllegalArgumentException(
                        "bind_to is invalid: network interface '" + interfaceName + "' has no address"));
    }
}
