/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function sendInited(){
  window.waitSend("{\"operation\":\"sendInited\"}");
}
function mouseDown(butt){
	window.waitSend("{\"operation\":\"mouseDown\",\"button\":\""+butt+"\"}");
}
function mouseUp(butt){
	window.waitSend("{\"operation\":\"mouseUp\",\"button\":\""+butt+"\"}");
}
function selectLocation(loc, locId){
	if(!isInTool())
		return;//ignore signals where we are in external browser
	locationId = locId; 	
	window.waitSend(JSON.stringify({
		'operation':"selectLocation",
		'locationId':locId
	}));	
}
//function onSelectedLocationChangeByClick(loc){
//	window.waitSend("{\"operation\":\"onSelectedLocationChangeByClick\", \"location\":\""+loc+"\"}");	
//}

function logInfo(message){	
	window.waitSend(JSON.stringify({
		'operation':"logInfo",
		'message':message
	}));
}
function logDebug(message){
	window.waitSend(JSON.stringify({
		'operation':"logDebug",
		'message':message
	}));
}
function logError(message){
	window.waitSend(JSON.stringify({
		'operation':"logError",
		'message':message
	}));	
}
function reportDocumentLoaded(location){
	window.waitSend("{\"operation\":\"reportDocumentLoaded\"}");
}
function initDocument(location, doc){
	if(doc!=null){
		window.waitSend(JSON.stringify({
			'operation':"initDocument",
			'document':doc
		}));
	}else
		window.waitSend("{\"operation\":\"initDocument\"}");
}
function callLoadLocations(location){
	window.waitSend("{\"operation\":\"callLoadLocations\"}");	
}
function removeFragmentCall(location, id, range){
	window.waitSend(JSON.stringify({
		'operation':"removeFragment",
		'locationId':id,
		'range':range
	}));
}
function removeLocation(id){
	window.waitSend(JSON.stringify({
		'operation':"removeLocation",
		'locationId':id
	}));
}

function createLocationLinkRequirement(locationRegions, locationIds, description, requuid){
	window.waitSend(JSON.stringify({
		'operation':"createLocationLinkRequirement",
		'locationRegions':locationRegions,
		'locationIds':locationIds,
		'description':description,
		'requuid':requuid
	}));
}
function createLocationCreateRequirement(locationRegions, locationIds, description, requuid, id, name){
	window.waitSend(JSON.stringify({
		'operation':"createLocationCreateRequirement",
		'locationRegions':locationRegions,
		'locationIds':locationIds,
		'description':description,
		'requrementParent':requuid,
		'requrementId':id,
		'requrementName':(!name?"":name)
	}));
}
function linkLocationLinkRequirement(locationId, requuid){
	window.waitSend(JSON.stringify({
		'operation':"linkLocationLinkRequirement",
		'locationId':locationId,
		'requuid':requuid
	}));
}
function linkLocationCreateRequirement(locationId,  requuid, id, name){
	window.waitSend(JSON.stringify({
		'operation':"linkLocationCreateRequirement",
		'locationId':locationId,
		'requrementParent':requuid,
		'requrementId':id,
		'requrementName':(name==null?"":name)
	}));
}
//here ends old-requality methods
var locationId = null;
var selection = null;
var serializer;
var gsel = "";

function getSerializer() {
  if (serializer == null && (typeof XMLSerializer != "undefined"))
    serializer = new XMLSerializer();
  return serializer;
}

// ----------------------------------------------------------------

function undoAction(actions) {
  if(!Array.isArray(actions))
    actions = [actions];
  actions.forEach(function(action,ind){
    switch (action.action) {
    case "create":
      if (!removeLocation(action.uuid));
        //return false;
      break;
    case "delete":
      restoreSelection(action.selection);
      if (!createLocation(action.uuid));
      //  return false;
      break;
    default:
      console.log("Unknown action '"
          + action.actionId
          + "'. Please, fill bug report at http://forge.ispras.ru/projects/reqdb/");
      //return false;initDocument
    }
  });
  return true;
}

function clearSelection(){
	console.log('csel');
  var selection = window.getSelection();
  selection.removeAllRanges();
}


function redoAction(actions) {
  if(!Array.isArray(actions))
    actions = [actions];
  actions.forEach(function(action,ind){
    switch (action.action) {
    case "create":
      restoreSelection(action.selection);
  
      if (!createLocation(action.uuid));
//        return false;
      break;
    case "delete":
      if (!removeLocation(action.uuid));
    //    return false;
      break;
    default:
      console.log("Unknown action '"
          + action.action
          + "'. Please, fill bug report at http://forge.ispras.ru/projects/reqdb/");
      //return false;
    }
  });
  return true;
}

// mark for
var textStart = false;

function getSelectionText() {
  return window.getSelection().toString().replace(/\r/g, "");
}


function getDocumentLocationIds() {
  var res = [];
  $(markup.fragment_class).each(function() {
    var id = this.getAttribute("id");
    res.push(id.substring(3));
  });
  return res;
}
function getNextNode(node) {
  //TODO rewrite
  if (node == null)
    return null;
  //printToConsole(node.toString()+" get Next ");
  if (node.firstChild)
    return node.firstChild;
  if (node.nextSibling)
    return node.nextSibling;
  node = node.parentNode;
  if (node == null)
    return null;
  var sib = node.nextSibling;
  while (sib == null && node != null) {
    node = node.parentNode;
    if(node)
      sib = node.nextSibling;
  }
  if (!sib)
    return null;
  return sib;
}
function getNextSibNode(node) {
  //TODO rewrite
  if (node == null)
    return null;
  if (node.nextSibling)
    return node.nextSibling;
  node = node.parentNode;
  if (node == null)
    return null;
  var sib = node.nextSibling;
  while (sib == null && node != null) {
    node = node.parentNode;
    if(node)
      sib = node.nextSibling;
  }
  if (!sib)
    return null;
  return sib;
}
function getPreviousNode(node) {
  //TODO rewrite
  if (node == null)
    return null;
  if (node.previousSibling)
    return node.previousSibling;
  return node.parentNode;
}

function checkReqClasses(node){
  if(node && node.classList &&
      (node.classList.contains(markup.fragment_class)
      || node.classList.contains(markup.fragment_class))){
      return true;
  }
  return false;
}

function getNodesForBorderedNodes(start, end,  locateLocations) {
//TODO rewrite
  if(locateLocations === undefined)
    locateLocations = true;
  if(!end)
	end = start;
  if(!start)
    start = end;
  if (!start || !end)
    return [];
  var nodes = [];
  var node, ln = start;
  var lastOne = getNextSibNode(end);
  var stop = false;
  if(locateLocations){
	  node = start.parentNode;
	  if(checkReqClasses(node))
	    nodes.push(node);
	  node = end.parentNode;
	  if(checkReqClasses(node))
	    nodes.push(node);
  }

  node = start;
  do{
    stop = node == lastOne;
    if(node){
      if(!locateLocations){
        nodes.push(node);
      }else{  
        if(!stop && checkReqClasses(node))
          nodes.push(node);
      }
    }
    node = getNextNode(node);
  }while(node && !stop);
  return nodes;
}
function parents(node) {
  var nodes = []
  for (; node; node = node.parentNode) {
    nodes.push(node)
  }
  return nodes
}
const ROOT_PROTOTYPE = Object.getPrototypeOf({});

function getAllKeys(object) {
    // do not add the keys of the root prototype object
    if (object === ROOT_PROTOTYPE) {
        return [];
    }

    const names = Object.getOwnPropertyNames(object);

    // remove the default constructor for each prototype
    if (names[0] === 'constructor') {
        names.shift();
    }

    // iterate through all the prototypes of this object, until it gets to the root object.
    return names.concat(getAllKeys(Object.getPrototypeOf(object)));
}

function moveToTextStart(range, startPoint){
  if(startPoint.nodeType == 3){
    if(startPoint.wholeText.length<=range.startOffset){
      var next = startPoint.nextSibling
      var pos = startPoint
      while (next == null && pos!=null){
        pos = pos.parentNode
        next = pos.nextSibling
      }
      if(next!=null)
        return next
    }
  }
  return startPoint;
}

function getNodesInRange(ranges, locateLocations) {
  if(locateLocations === undefined)
    locateLocations = true;
  for (var i = 0; i < ranges.rangeCount; i++) {
    var range = ranges.getRangeAt(i);
  //for(var i = 0;i<ranges.length;i++){
	var range = ranges.getRangeAt(0);
    var start = range.startContainer;
    start = moveToTextStart(range, start);
    var end = range.endContainer;
    return getNodesForBorderedNodes(start, end, locateLocations);
  //}
  }
}
function formTextIt(text, ids){
  var id = "";
  for(var i = 0;i<ids.length;i++){
    if(i>0)
      id+=", ";
    id+=text + ids[i];
  }
  return id;
}

var activeTextList=[], activeNodeList=[];

function deactivateLocations() {

  markup.clear_active();
}

function setSelection(nde) {
  console.log("setsel");
  var r = document.createRange();// tempr;
  var s = window.getSelection();
  s.removeAllRanges();
  r.selectNodeContents(nde);
  s.addRange(r);
  s.collapseToEnd();
}

function activateLocation(id, scroll) {
  //alert("activateLocation ("+id+") call "+getLocation());
  try{
  	deactivateLocations();
  }catch(ea){
    console.log("error during deactivate locs");
	console.log(ea+" "+ea.stack);
  }
  //markup.remove_highlights();
  var frag = markup.show_location(id);
  if(frag == null)
  	return;//TODO fix rangy problems with close ranges
  var loc = $("."+markup.fragment_active_class);
  if (scroll && loc && loc.offset) {
    window.scrollTo(0, loc.offset().top - window.innerHeight / 2);
  }
  //var selection = window.getSelection();
  //selection.removeAllRanges();
  //setSelection(loc.parentNode);
}
function activateWithReturnLocation(id, scroll) {
	  //alert("activateLocation ("+id+") call "+getLocation());
	  deactivateLocations();
	  var loc = document.getElementById("id_"+id);
	  if (loc == null || loc.tagName!=null&&loc.tagName!="a") {
	    return false;
	  }
	  var idnodes = document.getElementsByClassName("id_"+id);

	  for(var i=0;i<idnodes.length;i++){
	    var node = idnodes[i];
	    if(node.className === undefined){
	      activeTextList.splice(activeTextList.indexOf(node),1);
	      activeNodeList.splice(activeNodeList.indexOf(node),1);
	      i--;
	    }
	    var cl = node.className;
	    if(cl.indexOf("requality_node")>-1){
	      activeNodeList.push(node);
	      node.className+=" requality_node_active";
	    }
	    if(cl.indexOf("requality_text")>-1){
	      activeTextList.push(node);
	      node.className+=" requality_text_active";
	    }
	  }
	  var qloc = $(loc);
	  if (scroll && qloc && qloc.offset) {
	    window.scrollTo(0, qloc.offset().top - window.innerHeight / 2);
	  }
	  return loc;
	  //var selection = window.getSelection();
	  //selection.removeAllRanges();
	  //setSelection(loc.parentNode);
	}

function activateSelectedLocation(node) {
  var id = getSelectedLocationUUID(node);
  if (id !== null) {
    var loc = activateWithReturnLocation(id, true);
    var selection = window.getSelection();
    selection.removeAllRanges();
    return id;
  } else {
    deactivateLocations();
  }
  return null;
}

/**
 * Selecting requality Location node and makes it orange. <br>
 * <br>
 * <i>Can be used in java</i>
 */
function selectNode() {
  alert('selectNode');
}

function getBrowserInfo() {

  var appName = navigator.appName;
  var appAgent = navigator.userAgent;
  var appVersion = navigator.appVersion;
  return "Browser: " + appName + "; UserAgent: " + appAgent + "; Version: "
      + appVersion;
}

/**
 * <i>Can be used in java</i>
 * 
 * @return - <b>true</b> if selected node can be unwrapped
 */




function setOffset(elm, offset, point) {
  if(point == null)
  {
    printToConsole("null point!! "+elm+" "+offset);
    return;
  }
  point.offset = offset;
  // alert("setOffset, offset = " + offset + " point.nodeType = " +
  // point.nodeType );
  if (point.nodeType != 3)
    return;
  // alert("setOffset, text node" );
  var parent = elm.parentNode;
  for (var sib = elm.previousSibling; sib; sib = sib.previousSibling) {
    // alert("setOffset, previous sibling" );
    if (sib.nodeType != 3)
      continue;
    // alert("setOffset, previous sibling is text node" );
    point.offset += sib.textContent.length;
    // alert("setOffset, offset = " + point.offset );
  }
}

function createXPathFromElement(elm) {
  var allNodes = document.getElementsByTagName('*');
  var nodeType = Node.ELEMENT_NODE;
  if (elm.nodeType == Node.TEXT_NODE) // text node
  {
    elm = elm.parentNode;
    nodeType = Node.TEXT_NODE;
  }
  for (segs = []; elm && elm.nodeType == Node.ELEMENT_NODE; elm = elm.parentNode) {
    if (elm.hasAttribute('id')) {
      var uniqueIdCount = 0;
      for (var n = 0; n < allNodes.length; n++) {
        if (allNodes[n].hasAttribute('id') && allNodes[n].id == elm.id)
          uniqueIdCount++;
        if (uniqueIdCount > 1)
          break;
      }
      ;
      if (uniqueIdCount == 1) {
        segs.unshift('id("' + elm.getAttribute('id') + '")');
        return {
          node : segs.join('/'),
          nodeType : nodeType
        };
      } else {
        segs.unshift("x:" + elm.localName.toLowerCase() + '[@id="'
            + elm.getAttribute('id') + '"]');
      }
      // } else if (elm.hasAttribute('class')) {
      // segs.unshift("x:" + elm.localName.toLowerCase() + '[@class="' +
      // elm.getAttribute('class') + '"]');
    } else {
      for (i = 1, sib = elm.previousSibling; sib; sib = sib.previousSibling) {
        if (sib.localName == elm.localName)
          i++;
      }
      ;
      segs.unshift("x:" + elm.localName.toLowerCase() + '[' + i + ']');
    }
    ;
  }
  ;
  return segs.length ? {
    node : '/' + segs.join('/'),
    nodeType : nodeType
  } : null;
};
function createRangeDescriptor(range) {
  var start = createXPathFromElement(range.startContainer);
  setOffset(range.startContainer, range.startOffset, start);
  var end = createXPathFromElement(range.endContainer);
  setOffset(range.endContainer, range.endOffset, end);
  var text = getSerializer().serializeToString(range.cloneContents());
  return {
    start : start,
    end : end,
    text : text
  };
}

function propNames(obj) {
  if (!obj)
    return "obj is undefined";
  var s = "/*" + obj.toString() + ":*/{";
  var comma = false;
  for (p in obj) {
    if (comma)
      s += ",";
    comma = true;
    try {
      s += "|" + p + "=" + obj[p];
    } catch (e) {
      s += "|" + p + "=<not shown>";
    }
  }
  s += "\n}";
  return s;
}

function selectionDescriptorToString(selection) {
  var res = "";
  for (var i = 0; i < selection.length; i++) {
    res += rangeDescriptorToString(selection[i]) + "\n";
  }
  return res;
}

function rangeDescriptorToString(range) {
  var res = "start: " + pointToString(range.start) + "\n" + "end: "
      + pointToString(range.end) + "\n" + "text:" + range.text;
  return res;
}

function pointToString(point) {
  var res = "{ node:" + point.node + ", nodeType:" + point.nodeType
      + ", offset:" + point.offset + " }";
  return res;
}
function docInitFunc(){
	try{
		window.initStarted = true;
		sendInited();
	}catch(e){
		window.error = (!window.error?"":window.error)+"global"+(e+"+"+e.stack)+" "+e.message;
	}
}
var inited = false;
window.NO_MENU = false;
window.MENU_ACTIVE = false;
document.addEventListener("readystatechange", (event) => {
  if(!inited){
    inited = true;
	document.body.onclick = function(e){
		if(!window.MENU_ACTIVE){
			selectLoc(window.markup, "", null);
			//selectLocSend(window.markup, "");
        	window.markup.clear_active();
        }
	};
   }
});
document.addEventListener("keypress", (event) => {
  if(event.ctrlKey && (event.key.toUpperCase() == "D" || event.key==''))
    window.NO_MENU = !window.NO_MENU;
});
window.initStarted = false;
$( document ).ready(docInitFunc);
