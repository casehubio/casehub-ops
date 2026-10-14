package io.casehub.ops.claudony;

import java.util.List;
import java.util.Optional;

public interface ClaudonyFleetClient {

    ClaudonyAppInfo registerApp(ClaudonyAppSpec spec);

    ClaudonyAppInfo updateApp(String name, ClaudonyAppSpec spec);

    void deregisterApp(String name);

    Optional<ClaudonyAppInfo> getApp(String name);

    List<ClaudonyAppInfo> listApps(String namespace);

    void suspendApp(String name);

    void resumeApp(String name);

    void heartbeat(String name);
}
