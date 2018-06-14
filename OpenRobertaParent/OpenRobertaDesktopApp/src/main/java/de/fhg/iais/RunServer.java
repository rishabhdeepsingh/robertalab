package de.fhg.iais;

import de.fhg.iais.roberta.main.ServerStarter;

public class RunServer implements Runnable {
    public void run() {
        String[] strings =
                new String[] {
                        "-d",
                        "database.mode=embedded",
                        "-d",
                        "database.parentdir=../OpenRobertaServer",
                        "-d",
                        "server.staticresources.dir=../OpenRobertaServer/staticResources"
                };
        try {
            ServerStarter.main(strings);
            Thread.sleep(10000);
        } catch ( Exception e ) {
            e.printStackTrace();
        }
    }
}
