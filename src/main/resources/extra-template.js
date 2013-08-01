console.log("Validity", window.top.typeValidity);
window.top.typeValidity = window.top.typeValidity || {
    types: {},
    whenTypeFound: function(name, f) {
        var result = this.types[name];
        if (!result) {
            var outer = this.types[name] = {
                state: "loading",
                waiters: [f]
            };
            var url = "/docs/byType/"+name;
            $.ajax(url, {
                type: "HEAD",
                complete: function(xhr, status) {
                    outer.url = url;
                    if (xhr.status == 200) {
                        outer.state = "found";
                        outer.waiters.forEach(function(w) { w(url, outer); } );
                    } else outer.state = "missing";
                }
            });
        }
        else if (result.state == "loading")
            ;//result.waiters.push(f); // ignore for now, one callback per url should be enough
        else if (result.state == "found")
            f(result.url);
        else if (result.state == "missing")
            ;// don't do anything
    }
};

$(document).ready(function(){
    function replacer() {
        var old = $(this);
        var res = $('<a target="_parent"></a>');
        res.attr("href", "/docs/byType/"+old.attr("name"))
           .html(old.html());
        return res;
    }
    function tryReplacing(a, oldE) {
        var old = $(oldE);
        var tpe = old.attr("name");
        window.top.typeValidity.whenTypeFound(tpe, function(url) {
            $('span.extype[name="'+tpe+'"]').replaceWith(replacer);
        });
    }

    $("span.extype").each(tryReplacing);
});

