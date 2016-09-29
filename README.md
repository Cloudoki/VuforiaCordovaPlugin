# Vuforia Cordova Plugin (Android & iOS)
This plugin allows the application to detect targets and using Vuforia SDK, render 3D objects and play video.

## Android
- The plugin aims to be used with Android API >= 16 (4.1 Jelly Bean).


### Note
In *config.xml* add Android and iOS target preference
```javascript
<platform name="android">
    <preference name="android-minSdkVersion" value="16" />
</platform>
```

And don't forget to set the background to be transparent or the preview may not shown up.
Again in *config.xml* add the following preference.
```javascript
<preference name="backgroundColor" value="0x00000000" />
```

## License
Add the folder `license/` with a text file `vuforiaLicense.txt` in your project `www/` folder and put you license inside.

## Assests
Add the folder `assest/` in your project `www/` folder and put your 3D and video assests there with the following organization.

## Usage
The plugin offers the functions `isDetecting` and `setLang`.

**`isDetecting`** - the plugin will callback on success function if detecting the pattern or on error function if it's not. The response will also say what index of the patters set is being detected in a JSON object. Just parse it using `JSON.parse()`.
```javascript
isDetecting(successCallback, errorCallback);
```

**`setLang`** - this function will set a language in case you have Augmented Reality content that should be displayed in different languages. The file should be name with the language locale ("en") "filename_en.mp4" and when it's set it should load the file with the selected language.
```javascript
setLang(language, successCallback, errorCallback);
```

## Usage example
```javascript
  var _vuforiaCordovaPlugin = window.plugins.VuforiaCordovaPlugin || new VuforiaCordovaPlugin();
  _vuforiaCordovaPlugin.isDetecting(function(success){console.log(success);}, function(error){console.log(error);});
  _vuforiaCordovaPlugin.setLang("en", function(success){console.log(success);}, function(error){console.log(error);});
```
