# Vuforia Cordova Plugin (Android & iOS)
This plugin allows the application to detect targets and using Vuforia SDK, render 3D objects and play video.

### Note
The plugin is aimed to work in **portrait mode**, should also work in landscape but no guarantees.

## Android
- The plugin aims to be used with Android API >= 16 (4.1 Jelly Bean).

## iOS
- Due to Vuforia SDK requirements the plugin aims to be used with iOS version >= 8.

### Note
In *config.xml* add Android and iOS target preference
```javascript
<platform name="android">
    <preference name="android-minSdkVersion" value="16" />
</platform>
<platform name="ios">
    <preference name="target-device" value="handset"/>
    <preference name="deployment-target" value="8.0"/>
</platform>
```

And don't forget to set the background to be transparent or the preview may not shown up.
Again in *config.xml* add the following preference.
```javascript
<preference name="backgroundColor" value="0x00000000" />
```

## License
Add the folder `license/` with a text file `vuforiaLicense.txt` in your project `www/` folder and put you license inside.

## Assets
Add the folder `assets/` in your project `www/` folder and put your 3D and video assets there with the following organization.

### Assets iOS
Right now the plugin doesn't have Wavefront loader/parser for iOS so you need to convert it using `obj2opengl` and add it manually.

## Usage
The plugin offers the functions `isDetecting` and `setLang`.

**`isDetecting`** - the plugin will callback on success function if detecting the pattern or on error function if it's not. The response will also say the name of the pattern that is being detected in a JSON object. Just parse it using `JSON.parse()`.
```javascript
isDetecting(successCallback, errorCallback);
```
**Response:**
```javascript
{"state": true or false, "target": "pattern_name" or ""}
```

**`setLang`** - this function will set a language in case you have Augmented Reality content that should be displayed in different languages. The file should be name with the language locale ("en") "filename_en.mp4" and when it's set it should load the file with the selected language.
```javascript
setLang(language, successCallback, errorCallback);
```

**`autoPlay`** - this function will set a video you have available in the Augmented Reality content to automatically start to play.
```javascript
autoPlay(true or false, successCallback, errorCallback);
```
- **default**: auto-play is turned on.

## Usage example
```javascript
  var _vuforiaCordovaPlugin = window.plugins.VuforiaCordovaPlugin || new VuforiaCordovaPlugin();
  _vuforiaCordovaPlugin.isDetecting(function(success){console.log(success);}, function(error){console.log(error);});
  _vuforiaCordovaPlugin.setLang("en", function(success){console.log(success);}, function(error){console.log(error);});
  _vuforiaCordovaPlugin.autoPlay(true, function(success){console.log(success);}, function(error){console.log(error);});
```
