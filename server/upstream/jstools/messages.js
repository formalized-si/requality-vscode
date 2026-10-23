if(typeof logError === 'undefined')
	logError = function(str){
		console.log(str);
	};
if(typeof logInfo === 'undefined')
	logInfo = function(str){
		console.log(str);
	};
//window.addEventListener("message", receiveMessage, false);
var escapeHTML = function(unsafe) {
  return unsafe.replace(/[&<>"']/g, function(m) {
    switch (m) {
      case '&':
        return '&amp;';
      case '<':
        return '&lt;';
      case '>':
        return '&rt;';
      case '"':
        return '&quot;';
      default:
        return '&#039;';
    }
  });
};
function extescape(val){
	if(Array.isArray(val)){
		var ra = new Array();
		for(var c=0;c<val.length;c++)
		  ra[c] = extescape(val[c]);	
		return ra;
	}
	val = escapeHTML(val);
	if(val[0]!='\"')
	  val = '"'+val+'"';
	return val;
}

