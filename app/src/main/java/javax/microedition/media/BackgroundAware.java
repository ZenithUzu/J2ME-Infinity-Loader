/*
 * Copyright 2026 fuyijian (J2ME-Loader-HD), ZenithUzu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.media;

/**
 * A player that pauses itself while the app is in the background and resumes when it comes back.
 * Some games keep playing music after pauseApp(), or start it again, so the emulator does it.
 */
public interface BackgroundAware {
	void onBackgroundChanged(boolean background);
}
