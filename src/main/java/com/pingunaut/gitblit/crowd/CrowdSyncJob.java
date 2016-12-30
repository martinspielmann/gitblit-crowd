package com.pingunaut.gitblit.crowd;

public class CrowdSyncJob implements Runnable {

    private final CrowdConfigUserService service;

    public CrowdSyncJob(final CrowdConfigUserService service) {
        this.service = service;
    }

    @Override
    public void run() {
        this.service.syncUsers();
    }
}
