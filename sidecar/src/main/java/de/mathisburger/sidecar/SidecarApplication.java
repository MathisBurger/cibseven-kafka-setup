package de.mathisburger.sidecar;

import org.apache.camel.main.Main;

public class SidecarApplication {

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new OutboundRoute());
        main.configure().addRoutesBuilder(new OutboundHttpRoute());
        main.configure().addRoutesBuilder(new InboundRoute());
        main.run(args);
    }
}
