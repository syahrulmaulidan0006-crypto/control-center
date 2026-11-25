package com.example.controlcenter;

interface IShell {
    void destroy() = 16777114;
    int toggle(String target, boolean on) = 1;
    int connectWifi(String ssid, String security, String password) = 2;
}
