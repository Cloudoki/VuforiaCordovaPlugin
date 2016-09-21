/**
*
**/
var VuforiaCordovaPlugin = function () {};

VuforiaCordovaPlugin.prototype.greet = function (name, successCallback, errorCallback) {
  cordova.exec(successCallback, errorCallback, "VuforiaCordovaPlugin", "greet", [name]);
};

VuforiaCordovaPlugin.prototype.isDetecting = function (successCallback, errorCallback) {
  cordova.exec(successCallback, errorCallback, "VuforiaCordovaPlugin", "isDetecting", []);
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
