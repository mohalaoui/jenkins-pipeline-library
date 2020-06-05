import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

def call(Map data) {
    echo "Building release content with ${data.issues.total} issues ..."
    def template = libraryResource 'cd.api.promote.template'
    final Map<String, Object> binding = new HashMap<>();
    binding.put("build", data['build']);
    //binding.put("json", data['issues']);
    def releaseVersion = data['releaseVersion'] ?: data['env']['API_PL_VERSION'];
    binding.put("releaseVersion", releaseVersion);
    binding.put("env", data['env']);
    //def jiraUrl = data['jiraBaseUrl'] ?: 'https://' + data['env']['JIRA_HOST'];
    //binding.put("jiraUrl", jiraUrl);
    try {
      
      CompilerConfiguration cc = new CompilerConfiguration();
      cc.addCompilationCustomizers(new ImportCustomizer().addStarImports(
                "jenkins",
                "jenkins.model",
                "hudson",
                "hudson.model"));
      GroovyShell shell = new GroovyShell(new Binding(), cc);
      SimpleTemplateEngine engine = new SimpleTemplateEngine(shell);
      Template tmpl = engine.createTemplate(template);
      res  = tmpl.make(binding).toString();
      
      if (res != null) {
        return res.toString();
      }
    } catch (Exception e) {
        echo "Failed: " + e;
    }

    return "";
}
