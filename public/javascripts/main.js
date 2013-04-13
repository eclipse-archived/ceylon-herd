$(document).ready(function() {
    initSyntaxHighlighter();
    automaticSyntaxHighlighingPreTags();
    initStarRating();
});

function initSyntaxHighlighter() {
    try {
        if (SyntaxHighlighter != null) {
            SyntaxHighlighter.defaults['gutter'] = false;
            SyntaxHighlighter.defaults['toolbar'] = false;
            SyntaxHighlighter.all();
        }
    } catch (error) {
        if (error instanceof ReferenceError || error instanceof TypeError) {
            // ignore this one
        } else {
            throw error;
        }
    }
}

function automaticSyntaxHighlighingPreTags() {
    function enableSyntaxHighlighing() {
        var pre = $(this);
        if (!pre.hasClass("brush:")) {
            pre.addClass("brush: ceylon");
            pre.hide();
        }
    }

    $(".moduleDescription pre").each(enableSyntaxHighlighing);
    $(".moduleChangelog pre").each(enableSyntaxHighlighing);
    $(".comment pre").each(enableSyntaxHighlighing);
}

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