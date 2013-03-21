$(document).ready(function() {
    initSyntaxHighlighter();
    automaticSyntaxHighlighingPreTags();
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
    $(".comment pre").each(enableSyntaxHighlighing);
}