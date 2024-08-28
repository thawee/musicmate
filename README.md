Music Mate
=====================================

Managing music collection on Android phones and Android Music Player.


Pre-requisites
--------------

- Android 14 or Higher


Screenshots
-----------


Support
-------
/Users/thawee.p/Library/Android/sdk/platform-tools/adb shell dumpsys activity broadcasts

1. lis all broadcasts
$dumpsys activity broadcasts history

2. 
dumpsys media.audio_flinger

App Architecture
----------------
     UI - ViewModel - Repository


provider -- Data provider, db/rest
model - Model class (interface)
ui - fragment/activity
viewmodel - extends AndroidViewModel

AppExecutors.java -- 
xxxRepository.java - rep
XXXApp.java


License
-------

Copyright 2014 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.


git add .
git commit -m "update…"
git remote add origin https://github.com/thawee/musicmate.git
git push -u origin master 

proxy
set proxy in ~/.gradle/gradle.properties
systemProp.http.proxyHost=10.100.1.189
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=10.100.1.189
systemProp.https.proxyPort=8080


test DAV by curl
curl -X DELETE
curl -X OPTIONS
curl -X PROPFIND 

curl -X PROPFIND  http://10.100.1.189:8082/music/

sample dlna server
https://github.com/haku/dlnatoad

https://github.com/tobexyz/yaacc-code

https://github.com/jupnp
https://github.com/UniversalMediaServer/


DLNA servers use specific ports for communication. Here are the relevant ports:

TCP Port 5001: This is the default port for DLNA media streaming. If you’re using a different port number, make sure to use that value.
TCP Port 2869: Used for DLNA/UPnP discovery (SSDP). It helps devices find each other on the network.
UDP Port 1900: Also used for DLNA/UPnP discovery (SSDP).
