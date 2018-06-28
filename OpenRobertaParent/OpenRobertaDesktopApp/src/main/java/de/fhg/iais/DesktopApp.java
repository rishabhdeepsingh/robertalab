package de.fhg.iais;

public class DesktopApp {
    public static void main(String[] args) throws InterruptedException {
        Runnable server = new RunServer();
        Runnable app = new RunDesktopApp();
        Thread serverThread = new Thread(server);
        Thread appThread = new Thread(app);
        serverThread.start();
        Thread.sleep(3000);
        appThread.start();
    }
}