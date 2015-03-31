techlooper.directive("companyInfo", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-info.tem.html"
  }
}).directive("companySkills", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-skills.tem.html"
  }
}).directive("companyBenefits", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-benefits.tem.html"
  }
}).directive("companyOpportunities", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-opportunities.tem.html"
  }
}).directive("companyCulture", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-culture.tem.html",
    link: function(scope, elem, attrs) {
      $('[data-toggle="tooltip"]').tooltip({html:true, placement: 'top'});
      $('[data-toggle="cultureTooltip1"]').tooltip({html:true, placement: 'top'});
      $('[data-toggle="cultureTooltip2"]').tooltip({html:true, placement: 'top'});
      $('[data-toggle="cultureTooltip3"]').tooltip({html:true, placement: 'top'});
      $('[data-toggle="cultureTooltip4"]').tooltip({html:true, placement: 'top'});
    }
  }
}).directive("companyEmployee", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-employee.tem.html"
  }
}).directive("companyFollow", function () {
  return {
    restrict: "A",
    replace: true,
    templateUrl: "modules/talent-search/company-profile/company-follow.tem.html"
  }
});