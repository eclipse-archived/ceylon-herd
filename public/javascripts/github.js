function loadInfos(result){
	var image = result.data.owner.avatar_url;
	if(image){
		jQuery("#img-holder").append(jQuery("<img/>").attr('src', image));
	}
	var issues = result.data.open_issues;
	if(issues != null){
		jQuery("#issues").append(" ("+issues+" open issues)");
	}
    var descr = result.data.description;
    if(descr != null){
        jQuery("#description").text(descr);
    }
}
function loadModuleInfoFromGitHub(owner, project){
	jQuery(function (){
		jQuery.getJSON("https://api.github.com/repos/"+owner+"/"+project+"?callback=?", loadInfos);
	});
}
