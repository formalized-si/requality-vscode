window.markup = null;
if (!String.prototype.startsWith) {
    String.prototype.startsWith = function(searchString, position){
	  if(!searchString)
	    return false;
      return this.substr(position || 0, searchString.length) === searchString;
  };
}
try{
	function Markup(container_id, locations_list_obj) {
        window.markup = this;
        //this.setCnt(0);
	    this.container_id = container_id;
	    this.locations_list = locations_list_obj;
	    this.fragment_class = 'fragment';
	    this.fragment_checked_class = 'fragment-checked';
	    this.fragment_active_class = 'fragment-active';
	    this.locations = {};
	    this.fragment_location = {};
		this.appliedTargets = [];
		this.oldData = [];
	
	    // Initialize rangy
	    rangy.init();
	    this.init_highlighter();
	    this.init_locations();
        window.inited = true;
        //printToConsole("init");//first message to init communication
	}
	function selectLoc(current_instance, ida, node){
	    let fragment = current_instance.show_location(ida, node);
        if (fragment) {
            let container_obj = $('#'+current_instance.container_id);
            container_obj.scrollTop(container_obj.scrollTop() + fragment.position().top - container_obj.height() * 3/10);
        }
	}
	function selectLocSend(current_instance, ida){
	    selectLocation(getLocation(),ida);
	}
	function toSelectionString(selection){
		return selection.toImagedString();
	}
	function changeColor(id, cont, refine){
	  if(!cont)
	    cont = document.getElementsByClassName("id_"+id)[0];
      if(!cont && id!=null)
        return;
      var frags = document.getElementsByClassName("fragment");
      if(!frags)
        return;
      var j = 0;
      var last = -1;
      var fin = 0;
      for(var ia = 0;ia<frags.length;ia++){
		var dom = frags[ia];
        var cl = dom.className;
        var ind = cl.indexOf(" uid_");
        var indend = cl.indexOf("id_", ind+5);
        if(ind>-1){
          var part = cl.substring(ind+4, indend);
          if(part!=last){
            if(fin == 3)
              break;
            last = part;
            j++;
            if(fin == 0 && (id != null && part == id)){
              fin = 1;//id found, test next node to be with proper color
            }else{
              if(j % 2 == 0){//id not found or not set
			    dom.classList.remove("even");
		      }else{
		        dom.classList.add("even");
		      }
            }
            var firstFin = false;
            if(fin == 1){//test next node 
              if(dom.classList.contains("even")!=j%2==1){//if node is with proper color
                fin = 2;//test next
                firstFin = true;
              }else
                fin = 3;//stop on next color
            }
            if(firstFin && fin == 2 && dom.classList.contains("even")==j%2==1)//if we are in fin mode and 
              break;
            if(fin == 2 || fin == 3 || id == null){
			  if(dom.classList.contains("even")!=j%2==1){//if node 
				if(j % 2 == 0){
					dom.classList.remove("even");
				}else{
					dom.classList.add("even");
				}
			  }
		    }
          }else{
			if(dom.classList.contains("even")!=j%2==1){
              if(j % 2 == 0){
			    dom.classList.remove("even");
		      }else{
		        dom.classList.add("even");
		      }
			}
		  }
        }
      }
      if(fin == 0 && cont!=null){
        if(j % 2 == 0){
	      cont.classList.remove("even");
        }else{
          cont.classList.remove("odd");
        }
      }
	}
	Markup.prototype = {
		//cnt: 0,
//		setCnt:function(cnt2){
//			this.cnt = cnt2;
//		},
		getCnt:function(){
			return window.markup.highlighter.getNextId();
		},
		extractFid:function(elem){
	        //let current_instance = window.markup;
			for(var i = 0;i<elem.classList.length;i++){
				var cl = elem.classList[i];
				if(cl.startsWith("id_"))
					return cl.substring(3);
			}
			return null;
		},
		locateHighlight:function(id){
			if(!id)
				return null;
	        let current_instance = window.markup;
			var hls = current_instance.highlighter.highlights;
			var hl_obj = null;
			$.each(hls, function(i, elem){
				if(elem['id'] == id){
					hl_obj = elem;
					return false;
				}
			});
			return hl_obj;
		},
	    refresh: function() {
	        this.init_highlighter();
	        this.init_locations();
	    },
	    applyLocationClasses: function(){
//	        let current_instance = window.markup;
//			for(;current_instance.appliedTargets.length>0;){//current_instance.appliedTargets, function(i, cont){
//				var cont = current_instance.appliedTargets[0];
//				current_instance.appliedTargets.splice(0, 1);
//				if(!cont.isConnected)
//					continue;
//				hl_obj = window.markup.highlighter.getHighlightForElement(cont);
//		        if (hl_obj) {
//				    var id = hl_obj['id'];
//				    var uid = "uid_"+current_instance.fragment_location[id];
//				    if(!cont.classList.contains(uid))
//				    	cont.classList.add(uid);
//				    var id2 = "id_"+id;
//				    if(!cont.classList.contains(id2))
//				    	cont.classList.add(id2);
//				    
//				}
//			}
		},
	    init_highlighter: function () {
	        this.highlighter = rangy.createHighlighter(window.document, "textContent");
	        let current_instance = window.markup;
	        let at = current_instance.appliedTargets;
	        let applier = rangy.createClassApplier(this.fragment_class, {
	            ignoreWhiteSpace: true,
	            normalize: false,
	            tagNames: ["span"],
	            elementProperties: {
	                onclick: function(e) {
				        e.preventDefault();
				        e.stopPropagation();
	                    current_instance.clear_active();
	                    var uid = null;
	                    for(var i = 0;i<this.classList.length;i++){
							var cl = this.classList[i];
							if(cl.startsWith("uid_"))
								uid = cl.substring(4);
						}
						if(uid!=null){
		                    let ida = uid+'';
		                    selectLoc(current_instance, ida, this);
		                    selectLocSend(current_instance, ida);
		                    }

						//if(typeof(inTool) != "undefined" && inTool){
						//}
	                    return true;
	                }
	            },
	            classNameChecker:function(el){
					var classes = el.className.split(/\s+/);
					var wasside = false;
					$(classes).each(function(i,cl){
						if(cl!=current_instance.fragment_class && 
						!cl.startsWith("uid_") && !cl.startsWith("id_")
						&&(cl!=current_instance.fragment_checked_class)
						&&(cl!=current_instance.fragment_active_class))
							wasside = true;
					});
					return !wasside;
				},
	            onElementCreate:function(el, applier){
					if(el)//if is presented
						at.push(el);
				},
				afterElementCreate:function(el, highligter, applier){
					$(el).each(function(i, cont){
						//var cont = this;
						hl_obj = highligter;
				        if (hl_obj) {
						    var id = hl_obj['id'];
						    var ud = current_instance.fragment_location[id];
						    if(ud){
							    var uid = "uid_"+current_instance.fragment_location[id];
							    if(!cont.classList.contains(uid))
							    	cont.classList.add(uid);
						    }
						    var id2 = "id_"+id;
						    if(!cont.classList.contains(id2))
						    	cont.classList.add(id2);
						}
					});
					
				}
	        });
	        this.highlighter.addClassApplier(applier);
	    },
	
	    init_locations: function () {
          let current_instance = window.markup;
	      if (current_instance.locations) delete current_instance.locations;
	      current_instance.locations = {};
	    },
	
	    show_location: function(location_uuid, node) {
			let current_instance = window.markup;
			
			let loc = current_instance.locations[location_uuid];
//			
//			current_instance.clear_active();
			
			if(loc == null){
				//alert("no location "+location_uuid);
				return null;
			}
	        
	        first_found_fragment = null;
	        
	        
	        var location_data = current_instance.locations[location_uuid];
	        $.each(location_data['fragments'], function (i, fragment_id) {
		
	            $('.id_'+fragment_id).each(function () {
					var node = $(this);
		            if (!node.hasClass(current_instance.fragment_active_class)) {
		                if (!first_found_fragment) 
		                	first_found_fragment = node;
		                node.addClass(current_instance.fragment_active_class);
		            	node.addClass(current_instance.fragment_checked_class);
		            }
		        });
	        });
	        
	        
	        
	        return first_found_fragment;
	    },
	
	    add_location: function (location_uuid, location_data, location_ids) {
	        let current_instance = window.markup;
	        current_instance.clear_active();
			//let id = location_data["locationId"];
			let lid = location_uuid;
			location_data['uuid'] = lid;
	        current_instance.locations[lid] = location_data;
	        $.each(location_data['fragments'], function (i, fragment_id) {
	            current_instance.fragment_location['' + fragment_id] = lid;
	        });
	        var cl = 'item location-item uid_'+lid;
	        // Add new locations list item
	        let new_location_item = $('<div>', {
	            'class': cl,
	            data: {
	                uuid: lid
	            },
	            text: lid
	        });
	        new_location_item.click(function () {
	            current_instance.clear_active();
	            let fragment = current_instance.show_location(lid, this);
	            if (fragment) {
	                let container_obj = $('#'+current_instance.container_id);
	                container_obj.scrollTop(container_obj.scrollTop() + fragment.position().top - container_obj.height() * 3/10);
	            }
	            // console.log('Location:', $(this).data('uuid'), current_instance.get_location($(this).data('uuid')));
	        });
	        current_instance.locations_list.append(new_location_item);
	        
	        
		    if(location_ids){
			  $.each(location_ids, function (i, location_id) {
			    var uid = "uid_"+lid;
			    var cont = document.getElementsByClassName("id_"+location_id);
			    if(cont.length!=0){
				  cont = cont[0];
				  if(!cont.classList.contains(uid))
				  	cont.classList.add(uid);
				}
				//else it is initial load
			  });
		    }
	        
	    },
	
	
	
	    create_location: function (rselection_obj) {
			try{
		        let current_instance = window.markup;
		        let selection_obj = (typeof rselection_obj == "undefined")?rangy.getSelection():rselection_obj;
		        //current_instance.clear_active();
				//current_instance.highlighter.removeAllHighlights();
		        
		       // this.highlighter.removeAllHighlights();
		        let res;
				try{
					res = this.highlighter.highlightSelection(this.fragment_class, {
			            containerElementId: current_instance.container_id,
			            selection: selection_obj,
			            newRangeId: current_instance.getCnt()
			        });
				}catch(eax){
					alert(eax+" '"+eax.stack+"' '"+selection_obj+"' '"+rselection_obj+"'");
				}
		        if (!res || !res.length) alert("Cannot mark location: there is no selection");
		        let new_fragments = [], new_fragments_ids = [];
		        $.each(res, function (i, val) {
		            new_fragments.push({
		                "start": '' + (val['characterRange']['start']),
		                "end": '' + (val['characterRange']['end'])
		            });
		            new_fragments_ids.push(val.id);
		        });
				return {'fragments':JSON.stringify(new_fragments), "fragmentIds":JSON.stringify(new_fragments_ids), "seleciton":toSelectionString(selection_obj)};
			}catch(e){
				alert(e+" "+e.stack);
			}
	    },
	
	    remove_location: function (location_uuid) {
	        let current_instance = window.markup;
	        this.clear_active();
			let loc = current_instance.locations[location_uuid];
			if(loc == null)
				return;//already removed
	        let fragments = current_instance.locations[location_uuid]['fragments'];
	
	        // Remove fragments first
	        $.each(fragments, function (i, val) {
	            current_instance.remove_fragment(val);
	        });
	
	        // Remove location data
	        delete current_instance.locations[location_uuid];
	
	        // Remove location from list
	        this.locations_list.find('.location-item').each(function () {
	            if ($(this).data('uuid') === location_uuid) {
	                $(this).remove();
	                return false;
	            }
	        });
	        changeColor(null,null);
	    },
	
	    get_checked_fragment: function() {
	        let checked_fragment_obj = $('.'+window.markup.fragment_active_class);
	        if (!checked_fragment_obj.length) return null;
	        checked_fragment_obj = checked_fragment_obj[0];//TODO iterate
			var uid = null;
			var fid = null;
			for(var i = 0;i<checked_fragment_obj.classList.length;i++){
				var cl = checked_fragment_obj.classList[i];
				if(cl.startsWith("uid_"))
					uid = cl.substring(4);
				else if(cl.startsWith("id_"))
					fid = cl.substring(3);
			}
			if(uid == null || fid == null)
				return null;
			var hl_obj = null;
			var hls = this.highlighter.highlights;
			$.each(hls, function(i, elem){
				if(elem['id'] == fid){
					hl_obj = elem;
					return false;
				}
			});
			
	        if (hl_obj) {
	            return {
	                'id': fid,
	                'location': uid,
	                'range': {
	                    "start": '' + hl_obj['characterRange']['start'],
	                    "end": '' + hl_obj['characterRange']['end']
	                }
	            }
	        }
	        return null;
	    },
	
	    get_checked_fragment_text: function() {
	        let checked_fragment_obj = $('.'+window.markup.fragment_checked_class);
	        if (!checked_fragment_obj.length||checked_fragment_obj.length==0) return null;
	        checked_fragment_obj = checked_fragment_obj[0];
	        var fid = null;
			for(var i = 0;i<checked_fragment_obj.classList.length;i++){
				var cl = checked_fragment_obj.classList[i];
				if(cl.startsWith("id_"))
					fid = cl.substring(3);
			}
			if(fid == null)
				return null;
			var hl_obj = null;
			var hls = this.highlighter.highlights;
			$.each(hls, function(i, elem){
				if(elem['id'] == fid){
					hl_obj = elem;
					return false;
				}
			});
	        if (hl_obj) {
			    return hl_obj.getText();
	        }
	        return null;
	    },
	
	    remove_fragment: function(fragment_id) {
	        // Clear all fragment selections first
	        
	        let current_instance=window.markup;
	        this.clear_active();
	
	        // Remove fragment from location data
	        //let location_uuid = current_instance.fragment_location['' + fragment_id];
//			alert(location_uuid);
//			if(typeof (current_instance.locations[location_uuid]['fragments'])!="undefined")
//	        	current_instance.locations[location_uuid]['fragments'] = current_instance.locations[location_uuid]['fragments'].remove(fragment_id);
	
	        // Remove fragment data
	        delete current_instance.fragment_location[fragment_id];
			delete rangy_map[fragment_id];
	        // Unhighlight fragment
	        let highlights_to_remove = [];
	        $.each(this.highlighter.highlights, function (i, hl_obj) {
	            if (hl_obj['id'] == fragment_id) {
	                highlights_to_remove.push(hl_obj);
	            }
	        });
	        var imgs = $("img.id_"+fragment_id+",obj.id_"+fragment_id);
	        imgs.each(function( index ) {
              var img = $(this);
              var bareImg = this;
              img.removeClass("id_"+fragment_id);
              img.removeClass("fragment");
              img.removeClass("even");
              $.each(bareImg.classList, function (k, hl_obj3) {
                  if(typeof hl_obj3 != "undefined" && hl_obj3.startsWith("uid_"))
                    bareImg.classList.remove(hl_obj3);
                    if(bareImg.classList.length == 0)
                      bareImg.removeAttribute("class");
                });
	        });
	        if (highlights_to_remove.length) {
	          $.each(highlights_to_remove, function (i, hl_obj) {
	            var myId = "id_"+hl_obj['id'];
	            var ids = Array.from(document.getElementsByClassName(myId));
	            $.each(ids, function (j, hl_obj2) {
	              if(typeof hl_obj2 != "undefined" && typeof hl_obj2.classList != "undefined"){
                    hl_obj2.classList.remove(myId);
                    hl_obj2.classList.remove("even");
                    $.each(hl_obj2.classList, function (k, hl_obj3) {
	                  if(typeof hl_obj3 != "undefined" && hl_obj3.startsWith("uid_"))
	                    hl_obj2.classList.remove(hl_obj3);
                    });  
                  }
	            });
	          });
	          current_instance.highlighter.removeHighlights(highlights_to_remove);
	          //current_instance.highlighter.nextHighlightId--;
	          //current_instance.cnt = current_instance.highlighter.nextHighlightId-1;
	        }
	        changeColor(null,null);
	    },
	
	    clear_active: function () {
			let current_instance=window.markup;
			$("."+current_instance.fragment_active_class).each(function (i, val) {
				if(val)
					val.classList.remove(current_instance.fragment_active_class);
	        });
			
			$("."+current_instance.fragment_checked_class).each(function (i, val) {
				if(val)
					val.classList.remove(current_instance.fragment_checked_class);
	        });
			
	    },
	
	    remove_highlights: function (selector) {
	        let current_instance=window.markup, hl_objects = [];
	        selector.each(function () {
	        	var fid = extractFid(this);
	        	let hl_obj = locateHighlight(fid);
	            //let hl_obj = current_instance.highlighter.getHighlightForElement($(current_instance));
	            if (hl_obj) hl_objects.push(hl_obj);
	        });
	
	        this.clear_active();
	        if (hl_objects.length) current_instance.highlighter.removeHighlights(hl_objects);
	    },
	
	    remove_selected_highlights: function () {
	        let current_instance=window.markup;
	        current_instance.clear_active();
	        return current_instance.highlighter.unhighlightSelection();
	    },
	
	    remove_all_highlights: function () {
	        let current_instance=window.markup;
	        current_instance.clear_active();
	        current_instance.highlighter.removeAllHighlights();
	    },
	
	    serialize_highlights: function () {
	        let current_instance=window.markup;
	        return current_instance.highlighter.serialize();
	    },
		append_deserialize_highlights:function(data){
			let current_instance=window.markup;
	        current_instance.highlighter.append_deserialize(data);
		},
	    deserialize_highlights: function (data) {
	        let current_instance=window.markup;
	        current_instance.highlighter.deserialize(data);
	        oldData = data;
	    }
	}
	$(function() {
		try{
		    let locations_list = $('#locations_list');
		    new Markup('html_container', locations_list);
		}catch(inExc){
			alert(inExc+" "+inExc.stack);
		}
	});

}catch(eaxa){
	alert(eaxa+" "+eaxa.stack);
}