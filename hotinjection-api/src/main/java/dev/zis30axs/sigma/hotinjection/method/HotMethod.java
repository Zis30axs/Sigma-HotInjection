package dev.zis30axs.sigma.hotinjection.method;

import java.util.List;

public interface HotMethod {
    String getName();
    String invoke(MethodContext context, List<String> arguments) throws Exception;
}
