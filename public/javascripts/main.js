$(document).ready(function() {
    initStarRating();
    initSelectableTable();
    initSearchAndInfoShortcut();
});


function initStarRating() {
    $(".star-rating-editable > .star").on("click", function(e) {
        $(e.target).find(".star").removeClass("star-full");
        $(e.target).addClass("star-full");
        $(e.target).parentsUntil(".star-rating-editable").addClass("star-full");
        
        var ratingValue = $(e.target).parentsUntil(".star-rating-editable").length + 1;
        var starRating = $(e.target).parentsUntil(".header", ".star-rating-editable");
        starRating.find("input[name='rating']").val(ratingValue);
    });
    $(".star-rating-editable > .star-cancel").on("click", function(e) {
        var starRating = $(e.target).parent(".star-rating-editable");
        starRating.find(".star").removeClass("star-full");
        starRating.find("input[name='rating']").val("0");
    });
}

function initSelectableTable() {
    if( $(".table-selectable").length != 1 ) {
        return;
    }
    
    initMouseMoveHandler();
    initKeyboardShortcutsHandler();
    initKeyboardShortcutsInfo();

    function initMouseMoveHandler() {
        $(".table-selectable tr").mousemove(function () {
            selectRow($(this));
        });
    }
    
    function initKeyboardShortcutsHandler() {
        $(document).keypress(function(e) {
            if (isEventTargetInput(e)) {
                return;
            }
            if (e.charCode == 106 /* j */) {
                moveSelectionDown();
            }
            else if (e.charCode == 107 /* k */) {
                moveSelectionUp();
            }
            else if (e.keyCode == 13 /* enter */) {
                openSelectedRow();
            }
        });
    }
    
    function initKeyboardShortcutsInfo() {
        var p = $("#keyboard-shortcuts-panel");
        var k = $(".key-j, .key-k, .key-enter", p);
        k.find(".badge").addClass("badge-info");
        k.find(".muted").removeClass("muted").addClass("text-info");
    }
    
    function moveSelectionDown() {
        var trSelected = getSelectedRow();
        if( trSelected.length == 1 ) {
            selectRow(trSelected.next());
        }
        else {
            selectRow($(".table-selectable tbody tr:first"));
        }
    }
    
    function moveSelectionUp() {
        var trSelected = getSelectedRow();
        selectRow(trSelected.prev());
    }
    
    function openSelectedRow() {
        var trSelected = getSelectedRow();
        if (trSelected.length == 1) {
            trHref = $("a:first", trSelected).attr("href");
            if (trHref) {
                document.location = trHref;
            }
        }
    }
    
    function getSelectedRow() {
        return $(".table-selectable tbody tr.tr-selected");
    }
    
    function selectRow(row) {
        if (row.is("tr") && !row.hasClass("tr-selected")) {
            $(".table-selectable tbody tr.tr-selected").removeClass("tr-selected");
            row.addClass("tr-selected");
        }
    }  
}

function initSearchAndInfoShortcut() {
    var searchInput = $("input[name='q']")
    if (searchInput.length == 1) {
        $(document).keypress(function(e) {
            if (isEventTargetInput(e)) {
                return;
            }
            if (e.charCode == 115 /* s - focus search */) {
                e.preventDefault();
                searchInput.focus();
            }
            if (e.charCode == 63 /* ? - open info */) {
                $('#keyboard-shortcuts-dropdown > .dropdown-toggle').click();
            }
        });
        searchInput.keydown(function(e) {
            if (e.keyCode == 27 /* esc - unfocus seach */) {
                searchInput.blur();
            }
        });
    }
}

function isEventTargetInput(e) {
    var targetName = e.target.tagName.toLowerCase();
    if (targetName == "input" || targetName == "textarea") {
        return true;
    }
    return false;
}

function showHelp(){
    jQuery("#help").show();
}
function hideHelp(){
    jQuery("#help").hide();
}
function toggleHelp(){
    jQuery("#help").toggle();
}
jQuery(function(){
    // plug in the behaviour of expendable items
    jQuery("[data-behaviour=expandable]").each(function(i, elem){
        var $elem = jQuery(elem);
        jQuery("<div class='collapse-icons'>&#x25B4 &#x25B4 &#x25B4</div>").appendTo($elem);
        jQuery("<div class='expand-icons'>&#x25BE &#x25BE &#x25BE</div>").appendTo($elem);
        $elem.attr("title", "Click to expand/collapse");
        $elem.addClass("expandable");
        $elem.addClass("collapsed");
        $elem.click(function(){
            $elem.toggleClass("collapsed");
            //return false;
        });
    });
});
