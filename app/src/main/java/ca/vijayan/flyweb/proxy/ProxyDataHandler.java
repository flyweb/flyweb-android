package ca.vijayan.flyweb.proxy;

/**
 * Created by karui on 2/25/2017.
 */

public interface ProxyDataHandler {
    void handleLocalDataReceived(ProxyConnection conn, byte[] data);

    void handleLocalDataSent(ProxyConnection conn, int nbytes);

    void handleServiceDataReceived(ProxyConnection conn, byte[] data);

    void handleServiceDataSent(ProxyConnection conn, int nbytes);
}
