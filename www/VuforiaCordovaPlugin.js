/**
*
**/
var VuforiaCordovaPlugin = function () {};

VuforiaCordovaPlugin.prototype.isDetecting = function (successCallback, errorCallback) {
  cordova.exec(successCallback, errorCallback, "VuforiaCordovaPlugin", "isDetecting", []);
};

VuforiaCordovaPlugin.prototype.setLang = function (lang, successCallback, errorCallback) {
  cordova.exec(successCallback, errorCallback, "VuforiaCordovaPlugin", "setLang", [lang]);
};

VuforiaCordovaPlugin.prototype.autoPlay = function (autoplay, successCallback, errorCallback) {
  var autoplayOption = autoplay ? true : false;
  cordova.exec(successCallback, errorCallback, "VuforiaCordovaPlugin", "autoPlay", [autoplayOption]);
};

if (!window.plugins) {
  window.plugins = {};
}

if (!window.plugins.VuforiaCordovaPlugin) {
  window.plugins.VuforiaCordovaPlugin = new VuforiaCordovaPlugin();
}

if (typeof module != 'undefined' && module.exports){
  module.exports = VuforiaCordovaPlugin;
}
