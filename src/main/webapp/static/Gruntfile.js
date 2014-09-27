module.exports = function(grunt) {

   grunt.initConfig({
      pkg : grunt.file.readJSON("package.json"),

      includeSource : {
         options : {
            basePath : ".",
            duplicates : false,
            debug : true
         },
         target : {
            files : {
               'index.html' : 'index.tpl.html'
            }
         }
      },

      watch : {
         includeSource : {
            files : [ "default.tpl.html" ],
            tasks : [ "includeSource:target" ]
         },
         scripts : {
            files : [ "*.js" ],
            options : {
               livereload : true
            }
         },
         markup : {
            files : [ "*.html" ],
            options : {
               livereload : true
            }
         },
         stylesheets : {
            files : [ "*.css" ],
            options : {
               livereload : true
            }
         }
      },
      connect : {
         server : {
            options : {
               port : 8080,
               base : ".",
               keepalive : true
            }
         }
      }
   });

   grunt.loadNpmTasks("grunt-contrib-watch");
   grunt.loadNpmTasks("grunt-contrib-connect");
   grunt.loadNpmTasks("grunt-include-source");

   grunt.registerTask("build", [ "includeSource:target" ]);
   grunt.registerTask("run", [ "connect", "watch" ]);
};