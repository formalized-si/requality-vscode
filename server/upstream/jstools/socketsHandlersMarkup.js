handlers = [];
var initedCnt = 0;

handlers["init"] =function(op,current_instance, data){	
	//current_instance = window.markup;
	document.body.id = current_instance.container_id;
	var loc = ""; 
	if(typeof document!=="undefined" && data.document!="")
		initSource = data.document;
	if(typeof data.locationId!="undefined" && data.locationId!="")
		loc = data.locationId;
	reportDocumentLoaded(window.location.toString());
	initedCnt++;
	if(initedCnt == 1){
		handlers["initLocations"](op,current_instance,data);
		if(loc!="")
			activateLocation(loc,true);
		else
			deactivateLocations();
	}
};
window.readOnlyChanged = function (newVal){
  hideMenu();
}
handlers["readOnlyChange"] = function(op, current_instance,msg){
  window.readOnly = false;
  if(typeof msg['readOnly'] !== 'undefined' && msg['readOnly'] == 'true') 
    window.readOnly = true;
  window.readOnlyChanged(window.readOnly);
};
handlers["initLocations"]=function(op, current_instance, data){
  if(typeof data['readOnly'] !== 'undefined'){ 
    if(data['readOnly'] == 'true') 
      window.readOnly = true;
    window.readOnlyChanged(window.readOnly);
  }
	try{
		//current_instance.setCnt(0);
		current_instance.refresh();
		rangy_map = {};
		var lcs = data.locations;
		var ids = {};
		
		var c = current_instance.getCnt();
		var oldLocs = current_instance.locations?current_instance.locations:[];
		var markupChanges = false;
		var removedLocs = [];
		var removedFrags = [];
		var addedLoc = [];
		$.each(oldLocs, function (j, locOld){ 
		  removedLocs.push(locOld['uuid']);
		});
		var addedRangy = "";
		$.each(lcs, function (i, loc) {
			var was = false;
			var uuid = loc['uuid'];
			$.each(oldLocs, function (j, oldLoc){
				if(uuid==oldLoc['uuid'])
				{
					removedFrags[uuid] = [];
					removedLocs.slice(removedLocs.indexOf(oldLoc['uuid']),1);//this loc was found
					was = true;
					var nloc = [];
					var remLoc = [];
					$.each(loc['fragments'], function (f1, frag) {
						nloc.push(frag);
					});
					$.each(oldLoc['fragments'], function (f1, frag) {
						var ind = nloc.indexOf(frag);
						if(ind>-1)
						{	
							nloc.splice(ind, 1);
						}else{
							remLoc.push(frag);
						}
					});
					var locs = current_instance.locations[uuid];
					$.each(nloc, function (f1, frag) {
					  pos = frag.split("|");
					  var lid = c;
					  current_instance.fragment_location[lid] = uuid;
					  locs.push(lid);
					  rangy_map[lid] = uuid;
					  c++;
					  addedRangy+="|"+pos[0]+"$"+pos[1]+"$"+lid+"$fragment$html_container";
					});
					$.each(remLoc, function(fi, oldFrag){
					  removedFrags[uuid].push(oldFrag);
					});
				}
			});
			if(!was){
				addedLoc.push(loc);
				var uuid = loc['uuid'];
				current_instance.locations[uuid] = loc;
				ids[uuid] = new Array();
				$.each(loc['fragments'], function (j, fr) {
				  pos = fr.split("|");
				  var lid = c;
				  current_instance.fragment_location[lid] = uuid;
				  ids[uuid].push(lid);
				  rangy_map[lid] = uuid;
			      addedRangy+="|"+pos[0]+"$"+pos[1]+"$"+lid+"$fragment$html_container";
				  c++;
				});
			}
		});
		$.each(removedLocs, function(i, loc){//remove outdated locations
		  current_instance.remove_location(loc);//TODO rewrite by oldRangy manips
		});//will update markup
		var oldRangy = current_instance.serialize_highlights();
		$.each(removedFrags, function(uuid, frags){
	    	$.each(frags, function(fi, frag){
	    		var pos = frag.split("|");
	    		//locate start and end of frag
	    		var pstr = pos[0]+"$"+pos[1]+"$"; //form string in rangy format
	    		var ppos = oldRangy.indexOf(pstr);//find it
	    		var sfind = ppos+pstr.length; //and get end position for fragment in rangy
	    		var id = oldRandy.substring(sfind, oldRangy.indexOf("$", sfind+1));//get id of fragment
	    		delete current_instance.fragment_location[id];//delete some related info
	    		delete rangy_map[id];
	    		current_instance.locations[uuid].splice(current_instance.locations[uuid].indexOf(id),1);//and then delete fragment from locations
	    	});
		});
		oldRangy+=addedRangy;
		
		current_instance.deserialize_highlights(oldRangy);
		data['rangy_map'] = rangy_map;
		
		$.each(addedLoc, function (i, locs) {
			var uuid = locs["uuid"];
			current_instance.add_location(uuid,
			{
	    		'fragments': ids[uuid]
			}, ids[uuid]);
		});
        current_instance.applyLocationClasses();
        changeColor(null, null);
	}catch(e){
		console.log(e+" "+e.stack);	
	}
};
handlers["removeLocation"]=function(op, current_instance, data){
		current_instance.remove_location(data['uuid']);
};
function sleep(milliseconds) {
  const date = Date.now();
  let currentDate = null;
  do {
    currentDate = Date.now();
  } while (currentDate - date < milliseconds);
}

handlers["createLocation"]=function(op, current_instance, data){
	  try{
		var uid = data['uuid'];
		if(current_instance.locations[uid])
			return;//already tracked
		current_instance.locations[uid] = data;
		var frags = new Array();
		var rangy = "";
		var c = current_instance.getCnt();
		var lids = [];
		$.each(data['locationRegions'], function (j, loc) {
			pos = loc;
			frags.push(c);
			current_instance.fragment_location[c] = data['uuid'];
							
            rangy+=('|');
			rangy+=pos.start;
			rangy+=('$');
			rangy+=pos.end;
			rangy+=('$');
			rangy+=(c);
			rangy+=('$fragment$html_container');

			rangy_map[(c)+""] = data['uuid'];
			c++;
		});
		//current_instance.setCnt(c);
		data['rangy'] = "type:textContent"+rangy;
		data['rangy_map'] = rangy_map;
		data['fragments'] = frags;
		current_instance.append_deserialize_highlights(data['rangy']);
        current_instance.add_location(data['uuid'], {
            'fragments': frags}, frags);
        current_instance.applyLocationClasses();
        changeColor(null,null);
//	    var id = hl_obj['id'];
//	    var ud = current_instance.fragment_location[id];
	  }catch(e){console.log(e+" "+e.stack);}
};
handlers["setInitialLocation"]=function(op, current_instance, data){
	var loc = data['locationId'];
	activateLocation(loc, true);
}
handlers["activateLocation"]=function(op, current_instance, data){
	if(!isInTool())
		return;//ignore signals where we are in external browser
	var loc = data['locationId'];
	activateLocation(loc, true);
};
handlers["updateRequirement"]=function(op, current_instance, data){
	if(typeof data['requirementId'] == 'undefined' || data['requirement'] == '')
		window.requirement = "Requirements";
	else
		window.requirement = data['requirementId'];
	if(typeof data['nextId'] == 'undefined')
		window.nextId = "001";
	else
		window.nextId = data['nextId'];
	if(typeof data['requirementUUID'] == 'undefined'){
		window.requirementUUID = null;
	}else
		window.requirementUUID = data['requirementUUID'];
};
handlers["updateLocation"]=function(op, current_instance, data){
	if(!isInTool())
		return;//ignore signals where we are in external browser
	if(typeof data['locationId'] == 'undefined')
		return;
	else{
		var loc = data['locationId'];
		if(loc != "")
			activateLocation(loc, true);
		else
			deactivateLocations();
	}
};