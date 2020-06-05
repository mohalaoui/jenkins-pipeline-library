// Arg computeMavenVersions version: ${CURRENT_VERSION}
// Return releaseVersion and nextDevelopmentVersion
def call(Map data) {

  echo 'Computing maven versions from : ' + data['version']
  def currentVersion = data['version'].tokenize('.')
  echo 'Computing maven versions from : ' + currentVersion
  def version_first_digit = currentVersion[0]
  def version_second_digit=currentVersion[1]
  def version_third_digit=currentVersion[2].split('-')[0]
  
  // Calcul de la prochaine version de dev
  def next_version_third_digit = version_third_digit.toInteger() + 1
  
  return [ releaseVersion: "${version_first_digit}.${version_second_digit}.${version_third_digit}", 
  			nextDevelopmentVersion: "${version_first_digit}.${version_second_digit}.${next_version_third_digit}-SNAPSHOT" ] 
}