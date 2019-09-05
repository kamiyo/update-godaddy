# update-godaddy

A Clojure utility to update your GoDaddy dns A and AAAA records (for dynamic IP).

## Usage

Your GoDaddy key and secret should be set in either a `.lein-env` file, system environmental variables, or Java system properties. Note that the Clojure environ library takes ENV_VAR -> :env-var, env.var -> :env-var, etc.

If compiling to Uberjar, you must use Java properties or system environmental variables.

```
GODADDY_KEY=${key} GODADDY_SECRET=${secret} java -jar update-godaddy.jar
```
or
```
java -Dgodaddy.key=key -Dgodaddy.secret=secret -jar update-godaddy.jar
```

## Compiling

```
lein uberjar
```
Resulting standalone .jar can be found in `build/uberjar`.

## Running as service on Systemd

Move the .jar file to somewhere where you can remember, like `usr/local/bin`, and `chown` to root if you want, with the right permissions (755?).

Create a file in `/etc/systemd/system`, `update-godaddy.service`

The author only knows about systemd service files from what he learned on the internet, so this might not be the best example, but it works:

```
[Unit]
Description=Update Godaddy DNS IP for this server.
Wants=network-online.target
After=network-online.target

[Service]
Type=simple
ExecStart=ENV_VARS=env_var java -jar /usr/local/bin/update-godaddy.jar

[Install]
WantedBy=multi-user.target
```

You can put the environmental variables in the service file like so:
```
...
[Service]
Type=simple
Environment="GODADDY_KEY=key"
Environment="GODADDY_SECRET=secret"
ExecStart=...
```

`chmod` the service file to 644.

Then run:
```
sudo systemctl start update-godaddy
```
and check status with
```
sudo systemctl status update-godaddy
```

To enable service start on system boot:
```
sudo systemctl enable update-godaddy
```

## License

Copyright © 2019

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.
