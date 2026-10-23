function deleteCookie(name) {
  sessionStorage.removeItem(name);
}

function saveCookie(key, value){
  sessionStorage.setItem(key, value);
}
function getCookie(key, defvalue){
  var ret = sessionStorage.getItem(key);
  if(ret == null)
    ret = defvalue;
  return ret;
}
function getSessionAndPort(){
  var loc = window.location.href;
  const sloc = loc.split("/");
  var sessionpart=decodeURIComponent(sloc[6]);
  var ind = sessionpart.indexOf("-");
  if(ind>0){
    return [sessionpart.substring(0, ind), sessionpart.substring(ind+1)];
  }
  return [sessionpart, null];
}

var urlParamsString = function(name, val){
    var results = new RegExp('[\?&]' + name + '=([^&#]*)').exec(val);
    if (results == null){
       return null;
    }
    else {
       return decodeURI(results[1]) || 0;
    }
}
var urlParams = function(name){
	return urlParamsString(name, window.location.href);
};

var urlSessionPort = getSessionAndPort();
var sessionV = urlSessionPort[0];

var getPort = urlSessionPort[1];
if(!getPort)
  getPort = urlParams('port');
if(!getPort)
  getPort = getCookie(sessionV+"port", "");
var sourceA = urlParams('document');
if(!sourceA)
  sourceA = getCookie(sessionV+"document", "");
var inTool = urlParams('inTool');
if(!inTool)
  inTool = getCookie(sessionV+"inTool", "");
if(inTool || inTool == '1')
  inTool = true;


saveCookie(sessionV+"port", getPort);
saveCookie(sessionV+"source", sourceA);
saveCookie(sessionV+"inTool", inTool);