 //init handlers for worst case
var queryString = window.location.search;

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
function isInTool(){
    var urlSession = getSessionAndPort();
	const inTool3 = getCookie(urlSession[0]+"inTool", true);
	if(inTool3&&(inTool3==true || inTool3=="true"))
	  return true;
	const inTool2 = (typeof inTool === 'undefined' || inTool == null)?false:inTool;
	return inTool2;
}

function handleMessage(json){
	if (typeof(handlers) == 'undefined')
		handlers = [];
	function react(){
		var op = json.operation;
		var data = typeof(json.data)=='string'?JSON.parse(json.data):json.data;
		cnt = 1; 
   		let current_instance = window.markup;
   		if(typeof current_instance === "undefined" || !current_instance)
   			current_instance = {"id":"notMarkup"};
		try{

			if(op !== undefined){
				if(typeof sessionF !== 'undefined' && sessionF != null && sessionF==testSess){
					connected = true;//allowing to handle messages 
					//window.ws.onclose({"code":1001});//reset, wrong session;
				}
//				console.log("recieve: " +op);
				switch(op){
					case "readOnlyModeMessage":
						alert(data.message);
					break;
					case "readOnlyChange":
						window.readOnly = false;
						if(typeof data.readOnly !== 'undefined' && data.readOnly == "true")
							window.readOnly = true;
					break;
					case "init":
//						console.log("initRespondRecieved");
						connectAttempts = 0;//init completed
						var testSess = data.session;
						connected = true;
						if(typeof sessionF === 'undefined' || sessionF == null || sessionF!=testSess){
							connected = false;
							window.ws.onclose({"code":1001});//reset, wrong session;
						}
						window.readOnly = false;
						if(typeof data.readOnly !== 'undefined' && data.readOnly == "true")
							window.readOnly = true;
						
					break;
					case "closeConnection":
//						console.log("close Recieved");
						connectAttempts = MAX_CONNECTS+1;
						var testSess = json.session;
						var code = json.code;
						window.ws.onclose(json);//reset, wrong session;
					break;
				  }
				var handler = handlers[op];
				if(handler){
					handler(op,current_instance, data);
				}
			}
		}
		catch(e){
			alert(e+" "+e.stack);
		}
	};

	function reactTimeouted(){
	  if ((typeof window.inited !== "undefined") && window.inited) {
	    react();
	  } else {
	    setTimeout(reactTimeouted, 300); // try again in 300 milliseconds
	  }
	};
	reactTimeouted();

}
var connected = false;
var dropped = false;
const MAX_CONNECTS = 20;
var connectAttempts = 0;
var callBackQueque = [];
var posqueue = 0;
function waitForSocketConnection(socket, callback){
	socket = window.ws;
	if(connectAttempts>MAX_CONNECTS || dropped){
		return;
	}
	if(callback)
		callBackQueque.push(callback);
	var wait = false, sended = false;
	while(connected && socket.readyState == 1 && posqueue<callBackQueque.length){
		sended = true;
		var cb = callBackQueque[posqueue];
		posqueue++;
		if(cb){
	  		socket.send(cb);
	  		console.log("send: "+cb);
	  	}
		if(posqueue>10){
			callBackQueque.splice(0,10);
			posqueue-=10;
		}
	}
	var hasNodes = posqueue<callBackQueque.length;
	if(socket.readyState == 1 && !sended)
		wait = connectAttempts<MAX_CONNECTS;
	if(socket.readyState == 0)
		wait = connectAttempts<MAX_CONNECTS;
	else if(socket.readyState == 2)
		wait = connectAttempts<MAX_CONNECTS;
	else if(socket.readyState == 3)
	{
		initWebSocket(port, source);
		wait = connectAttempts<MAX_CONNECTS;
	}
	if(wait){
	 if(hasNodes)
    	setTimeout(
	        function () {
				waitForSocketConnection(socket, null);
	        }, 500); // wait 5 milisecond for the connection...
	}else if(!sended && hasNodes){
		window.log=(window.log?window.log:"")+("cannot send set of messages! "+socket.readyState+" "+session+" "+port+" "+document);
		alert(window.log);	
	}
}
function getLocation(){
	return window.location.toString();
}
let initTime = -1;
let onInit = [];
function checkCode(code){
	if (code == 1000){
		return true;//common case
        reason = "Normal closure, meaning that the purpose for which the connection was established has been fulfilled.";
    }else if(code == 1001){
    	return false;//also common case
        reason = "An endpoint is \"going away\", such as a server going down or a browser having navigated away from a page.";
    }else if(code == 1002)
        reason = "An endpoint is terminating the connection due to a protocol error";
    else if(code == 1003)
        reason = "An endpoint is terminating the connection because it has received a type of data it cannot accept (e.g., an endpoint that understands only text data MAY send this if it receives a binary message).";
    else if(code == 1004)
        reason = "Reserved. The specific meaning might be defined in the future.";
    else if(code == 1005)
        reason = "No status code was actually present.";
    else if(code == 1006)
       reason = "The connection was closed abnormally, e.g., without sending or receiving a Close control frame";
    else if(code == 1007)
        reason = "An endpoint is terminating the connection because it has received data within a message that was not consistent with the type of the message (e.g., non-UTF-8 [https://www.rfc-editor.org/rfc/rfc3629] data within a text message).";
    else if(code == 1008)
        reason = "An endpoint is terminating the connection because it has received a message that \"violates its policy\". This reason is given either if there is no other sutible reason, or if there is a need to hide specific details about the policy.";
    else if(code == 1009)
       reason = "An endpoint is terminating the connection because it has received a message that is too big for it to process.";
    else if(code == 1010) // Note that this status code is not used by the server, because it can fail the WebSocket handshake instead.
        reason = "An endpoint (client) is terminating the connection because it has expected the server to negotiate one or more extension, but the server didn't return them in the response message of the WebSocket handshake. <br /> Specifically, the extensions that are needed are: " + event.reason;
    else if(code == 1011)
        reason = "A server is terminating the connection because it encountered an unexpected condition that prevented it from fulfilling the request.";
    else if(code == 1015)
        reason = "The connection was closed due to a failure to perform a TLS handshake (e.g., the server certificate can't be verified).";
    else
        reason = "Unknown reason "+code;
    alert("Connection lost! " + reason);
    return false;
}
var timeOutAttempts = 0;

function reschelduleInit(){
	if(connectAttempts>MAX_CONNECTS || dropped)
		return;
	setTimeout(
		function () {
			initWebSocket(port,source);
		}, 3200);
}
function initWebSocket(port, source, funcOnInit){
	var time = new Date().getTime();
	if(funcOnInit)
	  onInit.push(funcOnInit);
	funcOnInit = null;
	if(time>initTime)
		initTime = time+3000;
	else
		return reschelduleInit();
    connectAttempts++;
    window.log = connectAttempts;
	if(connectAttempts>MAX_CONNECTS || dropped)
		return;
	var problems = false;
	if(window.ws && window.ws.readyState<2)
		return;
	try{
	    window.ws = new WebSocket("ws://127.0.0.1:"+port);
	    var ws = window.ws;
	    ws.onopen = function() {
	    	console.log("openned but  dropped=="+dropped);
			if(dropped)
				return; 
			timeOutAttempts = 0;
			window.log=window.log?window.log:"openned";
			//connected = true;
			var session = typeof sessionF === "undefined" ? "": sessionF;
			//TODO frames
			ws.send("{\"operation\":\"init\", \"location\":\""+window.location.toString()+"\",\"session\":\""+session+"\", \"document\":\""+source+"\"}");
		};
		ws.onerror = function(e){
			reschelduleInit();
		}
	    ws.onclose = function(e) {
          var reconn = checkCode(e?e.code?e.code:0:0);
          if(e.reason && e.reason.indexOf("TimeoutException")){
            timeOutAttempts++;
            if(timeOutAttempts<3)
          	  reconn = true;
          }
          if(!reconn){
          	dropped = true;
          }
          console.log("closed with reason =="+(e?e.code?e.code:0:0));
		  window.log=(window.log?window.log:"")+("\nclose "+connectAttempts);
		  //initTime = time+3000;
		  if(connected && reconn){
		  	connected = false;
		  	window.ws = null;
			if(!dropped && connectAttempts<=MAX_CONNECTS){
			  initWebSocket(port, source);
			}
	       } 
		};//try to reinit on disconnect
	    ws.waitSend = function(message){
			try{
				var jso = JSON.parse(message);
				if(!jso["location"])
				 	jso["location"] = getLocation();
				var session = typeof sessionF === "undefined" ? "": sessionF;
				 if(!jso["session"])
				 	jso["session"] = session;
				 waitForSocketConnection(window.ws, JSON.stringify(jso));
			}catch(e){
				alert(message+" "+e+" "+e.stack);
			}
		};
	    ws.onmessage = function(evt) {
	    	console.log("received "+(evt.data));
			try{
				var message = JSON.parse(evt.data);
	        	handleMessage(message);
			}
			catch(e){
				alert("non-requality message "+evt.data);
				alert(e);
				console.log(e);
			}
			//if(message.operation == "init")
	          //initDocument(window.location.toString());
	    };
		while(onInit.length>0){
		  var el = onInit[0];
		  if(el)
		  	el();
		  onInit.splice(0,1);
		}
	    window.log += ws?ws.readyState:"";
		window.addEventListener("unload",function(){
			if(ws && ws.readyState==2)
				ws.close();
		});
	}catch(e){
		problems = true;
		window.error = (!window.error?"":window.error)+"SOCKET"+(e+" "+e.stack);
		alert(e);
	    return;
	}
	if(!window.ws)
		problems = true;
	if(problems){
		reschelduleInit();
	    return;
	}
}

function waitSend(s){
	if(!window.ws)//TODO ready state?
		initWebSocket(port, source, function(){
			window.ws.waitSend(s);
		});
	else
		window.ws.waitSend(s);
	
}

function replaceUrlParam(url, paramName, paramValue)
{
  var last = url.lastIndexOf("#");
  var dotPart = "";
  if(last>-1)
  {
	dotPart = url.substring(last);
	url = url.substring(0,last);
  }
  if (paramValue == null) {
    paramValue = '';
  }
  paramName = encodeURIComponent(paramName);
  paramValue = encodeURIComponent(paramValue);
  var pattern = new RegExp('\\b('+paramName+'=).*?(&|#|$)');
  if (url.search(pattern)>=0) {
    return url.replace(pattern,'$1' + paramValue + '$2');
  }
  url = url.replace(/[?#]$/,'');
  return url + (url.indexOf('?')>0 ? '&' : '?') + paramName + '=' + paramValue+dotPart;
}
var urlSession = getSessionAndPort();
var sessionV = urlSession[0];

var getPort = urlSession[1];
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

var port, source;
if((typeof getPort === 'undefined' || getPort==null) ){
	//haven't get params
	port = getCookie(sessionV+"port", 9999);
	sourceA = source = getCookie(sessionV+"document", "");
	var loc = window.location.href;
	try{
		var url = 
		    replaceUrlParam(
		      replaceUrlParam(
		        replaceUrlParam(loc, "port", port),
		        "document",
		        source
		      ),
		      "session", sessionV
		    );
		window.location = url.toString();
	}catch(e){
		alert(e+" "+e.stack);
	}
	//window.location.replace(loc+(loc.indexOf("?")==-1?"?":"&")+"port="+port+"&document="+source);
}else{
	port = getPort;
	source = sourceA;
	//session = sessionV;

	window.onpagehide = function(event){
	  event.preventDefault();
	  event.returnValue = '';
	  return undefined;
	};
	var sessionF = sessionV;
	
	window.requirement = "Requirements";
	window.requirementUUID = null;
	window.nextId = "001";
	
	window.onbeforeunload = function() {
		halted = true;
		if(window.ws)
			window.ws.close(1000, "page reload");//TODO return back 1001?
	}
	window.waitSend = waitSend;
//	document.addEventListener('visibilitychange', function(){
//		if(document.visibilityState=='hidden'){
//			if(window.ws)
//				window.ws.close(1000, "vis change");
//		}
//	});
}

