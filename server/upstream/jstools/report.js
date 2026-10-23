
function selectNode(node){
	window.waitSend(	JSON.stringify({
			"operation": "openEditorForNode",
			"node":node==null?"null":node
		}));	
}
function callAddTest(node){
	window.waitSend(	JSON.stringify({
			"operation": "callAddTest",
			"node":node==null?"null":node
		}));	
}
function printToConsole(msg)
{
	window.waitSend(JSON.stringify({
			"operation": "printToConsole",
			"message":msg==null?"null":msg
		}));
}
function downloadLink(link)
{
	window.waitSend(JSON.stringify({
			"operation": "downloadLink",
			"link": link!=null?encodeURI(link):"null"
		}));
}
$(document).ready(function() {
   window.inited = true;
   var sessPort = getSessionAndPort();
   if(!sessPort){
     sessPort="NO_SESS";
   }
   printToConsole("init "+sessPort);//first message to init communication
   {
      $("a").each(function(){
         var doma = this;
         var qa = $(this);
         var href = qa.attr("href");
         
         if (qa.is("[download]")) {
           $.ajax({
             type: "HEAD", 
             url: href,
             cache:false,
             success: function(data, status, xhr)
             {
               var qa = $(doma);
               if(href.indexOf("://")>-1)
                 qa.attr("href", "javascript: downloadLink('"+href+"')");
               else
                 qa.attr("href", "javascript: downloadLink('"+(new URL(href,
                     window.location)).href+"')");
             }
           });
         }
      });
   }
});