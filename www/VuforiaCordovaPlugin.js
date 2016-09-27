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

if (!window.plugins) {
  window.plugins = {};
}

if (!window.plugins.VuforiaCordovaPlugin) {
  window.plugins.VuforiaCordovaPlugin = new VuforiaCordovaPlugin();
}

if (typeof module != 'undefined' && module.exports){
  module.exports = VuforiaCordovaPlugin;
}
