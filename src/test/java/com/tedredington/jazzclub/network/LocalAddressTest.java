package com.tedredington.jazzclub.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class LocalAddressTest {

    @Test
    void anAddressCanBeGivenBareOrWithCurlsHostPrefix() throws Exception {
        assertThat(LocalAddress.resolve("127.0.0.1")).isEqualTo(InetAddress.getByName("127.0.0.1"));
        assertThat(LocalAddress.resolve(" host!127.0.0.1 ")).isEqualTo(InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void anInterfaceResolvesToOneOfItsAddressesPreferringIpv4() throws Exception {
        NetworkInterface loopback = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));

        InetAddress viaPrefix = LocalAddress.resolve("if!" + loopback.getName());
        InetAddress bare = LocalAddress.resolve(loopback.getName());

        assertThat(Collections.list(loopback.getInetAddresses())).contains(viaPrefix);
        assertThat(viaPrefix.getAddress()).as("IPv4").hasSize(4);
        assertThat(bare).as("a bare interface name works like curl's").isEqualTo(viaPrefix);
    }

    @Test
    void anUnknownInterfaceOrHostIsAReadableError() {
        assertThatThrownBy(() -> LocalAddress.resolve("if!tun-does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bind_to is invalid: no network interface 'tun-does-not-exist'");
        assertThatThrownBy(() -> LocalAddress.resolve("host!no-such-host.invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot resolve");
    }
}
