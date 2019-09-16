# GemAuth

Spigot 1.12.2 auth plugin

### Features
- register / login on auth server
- saving data to MySQL server
- disabled events on auth server
- bungee bridge

### Installation

1. Put `GemAuth.jar` into spigot (auth) and bungee server plugins folder
2. Restart both servers to generate config
3. Rewrite the configs, save them and restart servers

### Configs

1. Spigot server:
```yaml
db: # Database settings
  host: 'localhost' 
  user: 'root'
  pass: '123'
  port: 3306 # default port is 3306
  db: 'mc' # database name

msg:
  login:
    chat: 'To login use /login <password>'
    title: ''
    subtitle: '/login <password>'
  register:
    chat: 'To register use /register <pass> <pass>'
    title: ''
    subtitle: '/register <pass> <pass>'
  timeout: 'Time out'
  log-a: 'You need to register first!'
  reg-a: 'You are already registered!'
  log-b: 'Wrong password!'
  reg-b: "Passwords don't match!"
  log-c: 'Logged successfully!'
  reg-c: 'Registered successfully!'
  log-d: 'You are already logged in!'
  reg-d: 'Password must be at least 6 chars!'

timeout: 40 # sec

spawnpoint: # where will player spawn
  world: world
  x: 0
  y: 0
  z: 0
  yaw: 0
  pitch: 0
```

2. Bungee server:
```yaml
auth-server: auth # Name of auth server from bungee config
lobby-server: # List of lobby servers
  - lobby
```