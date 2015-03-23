techlooper.factory("tsSearchResultService", function (tsMainService) {
  var $$ = {
    openURL: function(){
      $(document).delegate("[data-url]", 'click', function(e){
        e.preventDefault();
        var url = $(this).attr('data-url');
        if (url && url.length){
          window.location.href = url;
        }
      });
    },
    saveTalent: function(){
      $('.save-list').on('click', function(){
        $(this).addClass('saved');
      });
    }
  };
  var instance = {
    init: function () {
      $(".job-info span").dotdotdot({
        height: 23
      });
      $("span.location").dotdotdot({
        height: 23
      });
      $$.openURL();
      $$.saveTalent();
    },

    updateSearchText: function (request) {
      var searchRequest = tsMainService.getSearchRequest();
      for (var prop in request) {
        var options = [];
        var values = [];
        $.each(request[prop], function (i, text) {
          options.push({text: text});
          values.push(text);
        });
        if (searchRequest[prop] !== undefined) {
          searchRequest[prop].addOption(options);
          searchRequest[prop].setValue(values);
        }
      }
    }
  };

  return instance;

});