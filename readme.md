# kzen

### Download
- Windows zip: https://github.com/alexoooo/kzen-shell/releases/download/v0.28.1/kzen-0.28.1.zip
- Java 21 jar: https://github.com/alexoooo/kzen-shell/releases/download/v0.28.1/kzen-0.28.1-jars.zip

### Install
- Download for your operating system
- Extract zip file (e.g. C:\kzen)

### Run
- Double-click (or otherwise run) kzen.bat (or kzen.sh on Linux)
- Wait to load...
- Browser will open at: http://localhost:8080

### Notes
- Artifact downloads (launcher zip) validate TLS certificates with the JVM's default trust
  store. In environments with TLS-intercepting proxies (corporate MITM), supply your own trust
  store via `-Djavax.net.ssl.trustStore=<path>` when launching.

### Screenshots
See: https://github.com/alexoooo/kzen-shell/wiki/Screenshots

Example:
![image](https://user-images.githubusercontent.com/4985552/142746508-a91844fd-6de4-4683-8ccc-0292e352eb1a.png)
